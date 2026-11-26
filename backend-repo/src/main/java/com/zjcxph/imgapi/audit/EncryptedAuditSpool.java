package com.zjcxph.imgapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Opt-in POSIX laboratory candidate. No plaintext file or key is ever persisted.
 * One exclusive owner per private directory; a published record survives JVM termination.
 */
public final class EncryptedAuditSpool implements AutoCloseable {
    private static final int VERSION = 1, GROUP_VERSION = 2, KEYED_VERSION = 3, MAX_GROUP = 16, NONCE_BYTES = 12, TAG_BITS = 128, MAX_RECORD = 64 * 1024;
    private static final Pattern PENDING = Pattern.compile("[0-9a-f-]{36}\\.pending");
    private static final Pattern TEMP = Pattern.compile("[0-9a-f-]{36}\\.tmp");
    private static final Set<PosixFilePermission> PRIVATE_DIR = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private final Path root;
    private final ObjectMapper mapper;
    private final SecretKeySpec key;
    private final Map<String,SecretKeySpec> keys;
    private final String activeKeyId;
    private final int maxRecords;
    private final long maxBytes;
    private final SecureRandom random = new SecureRandom();
    private record Segment(long bytes, int records) {}
    private final LinkedHashMap<String, Segment> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, Segment> held = new LinkedHashMap<>();
    private static final Pattern HELD = Pattern.compile("[0-9a-f-]{36}\\.held");
    private static final Pattern KEY_ID=Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private int records;
    private long publishedSegments, publishedBytes, fileForces, directoryForces;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private long bytes;
    private boolean closed;

    public record Entry(String name, Log log) {}
    public static final class CapacityException extends IOException {
        public CapacityException() { super("Audit spool capacity exceeded"); }
    }

    public EncryptedAuditSpool(Path directory, byte[] rawKey, int maxRecords, long maxBytes, ObjectMapper mapper) throws IOException {
        this(directory,rawKey,Map.of(),null,maxRecords,maxBytes,mapper);
    }

    public EncryptedAuditSpool(Path directory,byte[] rawKey,Map<String,byte[]> keyRing,String activeKeyId,
                               int maxRecords,long maxBytes,ObjectMapper mapper)throws IOException {
        if (rawKey == null || rawKey.length != 32 || maxRecords <= 0 || maxRecords > 100_000
                || maxBytes < MAX_RECORD || maxBytes > 1024L * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid audit spool configuration");
        }
        this.root = directory.toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper);
        this.key = new SecretKeySpec(rawKey, "AES");
        if(keyRing.size()>8 || (activeKeyId==null&&!keyRing.isEmpty()) || (activeKeyId!=null&&!keyRing.containsKey(activeKeyId)))
            throw new IOException("Invalid audit key ring");
        var configured=new HashMap<String,SecretKeySpec>();
        for(var entry:keyRing.entrySet()){
            if(!KEY_ID.matcher(entry.getKey()).matches()||entry.getValue()==null||entry.getValue().length!=32)
                throw new IOException("Invalid audit key ring");
            configured.put(entry.getKey(),new SecretKeySpec(entry.getValue(),"AES"));
        }
        this.keys=Map.copyOf(configured);this.activeKeyId=activeKeyId;
        this.maxRecords = maxRecords;
        this.maxBytes = maxBytes;
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.equals(root.toRealPath())
                || !Files.getFileStore(root).supportsFileAttributeView("posix")
                || !Files.getPosixFilePermissions(root).equals(PRIVATE_DIR)
                || !Files.getOwner(root).equals(root.getFileSystem().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name")))) {
            throw new IOException("Audit spool requires an existing owner-private POSIX directory");
        }
        Path lockPath = root.resolve(".owner.lock");
        lockChannel = FileChannel.open(lockPath, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
        FileLock acquired = null;
        try {
            if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getPosixFilePermissions(lockPath).equals(PRIVATE_FILE)) {
                throw new IOException("Invalid audit spool lock");
            }
            acquired = lockChannel.tryLock();
            if (acquired == null) throw new IOException("Audit spool already owned");
            load();
            syncDirectory();
        } catch (IOException | RuntimeException failure) {
            if (acquired != null) acquired.release();
            lockChannel.close();
            throw new IOException("Audit spool initialization failed", failure instanceof CapacityException ? failure : null);
        }
        lock = acquired;
    }

    private void load() throws IOException {
        try (var files = Files.newDirectoryStream(root)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (name.equals(".owner.lock")) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !Files.getPosixFilePermissions(path).equals(PRIVATE_FILE)
                        || !Files.getOwner(path).equals(Files.getOwner(root))) throw new IOException("Invalid audit spool file");
                // Temporary files were never acknowledged by append; they are not published records.
                if (TEMP.matcher(name).matches()) { Files.delete(path); continue; }
                boolean heldFile=HELD.matcher(name).matches();
                if (!PENDING.matcher(name).matches()&&!heldFile) throw new IOException("Unknown audit spool file");
                long size = Files.size(path);
                if (size > (long) MAX_GROUP * (MAX_RECORD + 1) + 128 || size < 30) throw new IOException("Invalid audit spool frame size");
                if (bytes + size > maxBytes) throw new CapacityException();
                int count = decode(heldFile?pendingName(name):name, path).size(); // Wrong key or corruption blocks startup.
                if (records + count > maxRecords) throw new CapacityException();
                (heldFile?held:pending).put(name,new Segment(size,count)); bytes += size; records += count;
            }
        }
    }

    public synchronized void append(Log log) throws IOException {
        publish(List.of(log), activeKeyId==null?VERSION:KEYED_VERSION);
    }

    /** Sealed append-only segment: all callers are acknowledged only after both durable barriers. */
    public synchronized void appendGroup(List<Log> rows) throws IOException {
        publish(rows, activeKeyId==null?GROUP_VERSION:KEYED_VERSION);
    }

    private void publish(List<Log> rows, int version) throws IOException {
        requireOpen();
        if (rows.isEmpty() || rows.size() > MAX_GROUP) throw new IllegalArgumentException("Invalid audit segment");
        if (records + rows.size() > maxRecords) throw new CapacityException();
        var serialized = new ArrayList<byte[]>(rows.size());
        byte[] plain = null;
        try {
            int length=version==VERSION?0:2+rows.size()-1;
            for (Log log : rows) {
                if (log == null || log.getRequestId() == null || log.getRequestId().isBlank()
                        || log.getAccessTime() == null) throw new IOException("Audit spool requires request identity and time");
                byte[] row;
                try { row=mapper.writeValueAsBytes(log); } catch(Exception failure) { throw new IOException("Audit serialization failed"); }
                serialized.add(row);
                if(row.length>MAX_RECORD) throw new CapacityException();
                length+=row.length;
            }
            ByteBuffer payload=ByteBuffer.allocate(length);
            if(version!=VERSION) payload.put((byte)'[');
            for(int i=0;i<serialized.size();i++){
                if(i>0) payload.put((byte)',');
                payload.put(serialized.get(i));
            }
            if(version!=VERSION)payload.put((byte)']');
            plain=payload.array();
            String id=UUID.randomUUID().toString(), name=id+".pending";
            byte[] nonce=new byte[NONCE_BYTES];random.nextBytes(nonce);
            String keyId=version==KEYED_VERSION?activeKeyId:null;
            byte[] keyHeader=keyId==null?new byte[0]:keyId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] cipher=crypt(Cipher.ENCRYPT_MODE,version,name,keyId,nonce,plain);
            ByteBuffer framed=ByteBuffer.allocate(1+(keyId==null?0:1+keyHeader.length)+nonce.length+cipher.length).put((byte)version);
            if(keyId!=null)framed.put((byte)keyHeader.length).put(keyHeader);
            byte[] frame=framed.put(nonce).put(cipher).array();
            if(bytes+frame.length>maxBytes)throw new CapacityException();
            Path temporary=root.resolve(id+".tmp"),destination=root.resolve(name);
            try {
                try(FileChannel channel=FileChannel.open(temporary,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),
                        PosixFilePermissions.asFileAttribute(PRIVATE_FILE))){
                    ByteBuffer buffer=ByteBuffer.wrap(frame);
                    while(buffer.hasRemaining())channel.write(buffer);
                    channel.force(true);fileForces++;
                }
                Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
                pending.put(name,new Segment(frame.length,rows.size()));bytes+=frame.length;records+=rows.size();
                syncDirectory();
                publishedSegments++;publishedBytes+=frame.length;
            } finally {Files.deleteIfExists(temporary);}
        } finally {
            for(byte[] row:serialized)Arrays.fill(row,(byte)0);
            if(plain!=null)Arrays.fill(plain,(byte)0);
        }
    }

    public synchronized List<Entry> peek(int limit) throws IOException {
        requireOpen();
        if(limit<1||limit>200)throw new IllegalArgumentException("Invalid spool batch");
        List<Entry> result=new ArrayList<>();
        for(var item:pending.entrySet()){
            if(result.size()+item.getValue().records()>limit) {
                if(result.isEmpty())throw new IOException("Delivery batch smaller than sealed segment");
                break;
            }
            String name=item.getKey();
            List<Log> rows=decode(name,root.resolve(name));
            if(rows.size()!=item.getValue().records())throw new IOException("Audit segment changed");
            for(Log row:rows)result.add(new Entry(name,row));
        }
        return result;
    }

    /** A sealed segment can only be acknowledged as a whole after database commit. */
    public synchronized void acknowledge(String name) throws IOException {
        acknowledgeSegments(List.of(name));
    }

    public synchronized void acknowledgeSegments(Collection<String> names) throws IOException {
        requireOpen();
        var distinct=new LinkedHashSet<>(names);
        for(String name:distinct)if(!pending.containsKey(name))throw new IOException("Unknown audit segment");
        for(String name:distinct){
            Segment segment=pending.get(name);
            Files.delete(root.resolve(name));
            pending.remove(name);bytes-=segment.bytes();records-=segment.records();
        }
        if(!distinct.isEmpty())syncDirectory();
    }

    private List<Log> decode(String name,Path path) throws IOException {
        byte[] frame;
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            frame=input.readNBytes(MAX_GROUP*(MAX_RECORD+1)+129);
        }
        if(frame.length<30||frame.length>MAX_GROUP*(MAX_RECORD+1)+128
                ||(frame[0]!=VERSION&&frame[0]!=GROUP_VERSION&&frame[0]!=KEYED_VERSION))throw new IOException("Invalid audit frame");
        int version=frame[0];
        if(version==VERSION&&frame.length>MAX_RECORD+64)throw new IOException("Invalid single audit frame");
        String keyId=null;int start=1;
        if(version==KEYED_VERSION){
            int len=Byte.toUnsignedInt(frame[start++]);
            if(len<1||len>32||frame.length<start+len+NONCE_BYTES+16)throw new IOException("Invalid audit key identifier");
            keyId=new String(frame,start,len,java.nio.charset.StandardCharsets.US_ASCII);start+=len;
            if(!KEY_ID.matcher(keyId).matches()||!keys.containsKey(keyId))throw new IOException("Unknown audit key identifier");
        }
        byte[] plain=crypt(Cipher.DECRYPT_MODE,version,name,keyId,Arrays.copyOfRange(frame,start,start+12),Arrays.copyOfRange(frame,start+12,frame.length));
        try {
            List<Log> rows=version==VERSION?List.of(mapper.readValue(plain,Log.class)):
                    mapper.readValue(plain,new com.fasterxml.jackson.core.type.TypeReference<List<Log>>(){});
            if(rows.isEmpty()||rows.size()>MAX_GROUP||rows.stream().anyMatch(row->row==null||row.getRequestId()==null||row.getRequestId().isBlank()||row.getAccessTime()==null))
                throw new IOException("Invalid audit records");
            return rows;
        } catch(Exception failure){throw new IOException("Invalid encrypted audit payload");}
        finally{Arrays.fill(plain,(byte)0);}
    }

    private byte[] crypt(int mode,int version,String name,String keyId,byte[] nonce,byte[] input)throws IOException{
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,keyId==null?key:keys.get(keyId),new GCMParameterSpec(TAG_BITS,nonce));
            cipher.updateAAD(("mrr-audit-v"+version+":"+name+(keyId==null?"":":"+keyId)).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        }catch(GeneralSecurityException failure){throw new IOException("Audit authentication/encryption failed");}
    }

    private static String pendingName(String heldName){
        return heldName.substring(0,heldName.length()-5)+".pending";
    }

    /** Preserve ciphertext and budget. Only deterministic conflicts/explicit age policy may call this. */
    public synchronized void hold(String name)throws IOException{
        requireOpen();
        Segment segment=pending.get(name);
        if(segment==null)throw new IOException("Unknown audit segment");
        String destination=name.substring(0,name.length()-8)+".held";
        Files.move(root.resolve(name),root.resolve(destination),StandardCopyOption.ATOMIC_MOVE);
        pending.remove(name);held.put(destination,segment);
        syncDirectory();
    }
    public synchronized int heldCount(){return held.values().stream().mapToInt(Segment::records).sum();}

    private void syncDirectory() throws IOException {
        try (FileChannel channel = FileChannel.open(root, StandardOpenOption.READ)) { channel.force(true); directoryForces++; }
    }

    private void requireOpen() throws IOException { if (closed) throw new IOException("Audit spool closed"); }
    public synchronized int pendingCount() { return records; }
    public synchronized long publishedSegments(){return publishedSegments;}
    public synchronized long publishedBytes(){return publishedBytes;}
    public synchronized long fileForces(){return fileForces;}
    public synchronized long directoryForces(){return directoryForces;}
    public synchronized long pendingBytes() { return bytes; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
