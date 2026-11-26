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
    private static final int VERSION = 1, NONCE_BYTES = 12, TAG_BITS = 128, MAX_RECORD = 64 * 1024;
    private static final Pattern PENDING = Pattern.compile("[0-9a-f-]{36}\\.pending");
    private static final Pattern TEMP = Pattern.compile("[0-9a-f-]{36}\\.tmp");
    private static final Set<PosixFilePermission> PRIVATE_DIR = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private final Path root;
    private final ObjectMapper mapper;
    private final SecretKeySpec key;
    private final int maxRecords;
    private final long maxBytes;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, Long> pending = new LinkedHashMap<>();
    private final FileChannel lockChannel;
    private final FileLock lock;
    private long bytes;
    private boolean closed;

    public record Entry(String name, Log log) {}
    public static final class CapacityException extends IOException {
        public CapacityException() { super("Audit spool capacity exceeded"); }
    }

    public EncryptedAuditSpool(Path directory, byte[] rawKey, int maxRecords, long maxBytes, ObjectMapper mapper) throws IOException {
        if (rawKey == null || rawKey.length != 32 || maxRecords <= 0 || maxRecords > 100_000
                || maxBytes < MAX_RECORD || maxBytes > 1024L * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid audit spool configuration");
        }
        this.root = directory.toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper);
        this.key = new SecretKeySpec(rawKey, "AES");
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
                if (!PENDING.matcher(name).matches()) throw new IOException("Unknown audit spool file");
                long size = Files.size(path);
                if (size > MAX_RECORD + 64 || size < 30) throw new IOException("Invalid audit spool frame size");
                if (pending.size() >= maxRecords || bytes + size > maxBytes) throw new CapacityException();
                decode(name, path); // Wrong key, corruption or unsupported version blocks startup.
                pending.put(name,size); bytes += size;
            }
        }
    }

    public synchronized void append(Log log) throws IOException {
        requireOpen();
        if (log == null || log.getRequestId() == null || log.getRequestId().isBlank()
                || log.getAccessTime() == null) throw new IOException("Audit spool requires request identity and time");
        if (pending.size() >= maxRecords) throw new CapacityException();
        String id = UUID.randomUUID().toString();
        String name = id + ".pending";
        byte[] plain;
        try { plain = mapper.writeValueAsBytes(log); } catch (Exception failure) { throw new IOException("Audit serialization failed"); }
        try {
            if (plain.length > MAX_RECORD) throw new CapacityException();
            byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
            byte[] cipher = crypt(Cipher.ENCRYPT_MODE, name, nonce, plain);
            byte[] frame = ByteBuffer.allocate(1 + nonce.length + cipher.length).put((byte)VERSION).put(nonce).put(cipher).array();
            if (bytes + frame.length > maxBytes) throw new CapacityException();
            Path temporary = root.resolve(id + ".tmp");
            Path destination = root.resolve(name);
            try {
                try (FileChannel channel = FileChannel.open(temporary, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                        PosixFilePermissions.asFileAttribute(PRIVATE_FILE))) {
                    ByteBuffer buffer = ByteBuffer.wrap(frame);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                // Keep the record indexed even if directory force reports an uncertain result.
                pending.put(name, (long) frame.length); bytes += frame.length;
                syncDirectory();
            } finally { Files.deleteIfExists(temporary); }
        } finally { Arrays.fill(plain, (byte)0); }
    }

    public synchronized List<Entry> peek(int limit) throws IOException {
        requireOpen();
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Invalid spool batch");
        List<Entry> result = new ArrayList<>();
        for (String name : pending.keySet()) {
            if (result.size() == limit) break;
            result.add(new Entry(name, decode(name, root.resolve(name))));
        }
        return result;
    }

    /** Caller must establish committed database outcome before acknowledging. */
    public synchronized void acknowledge(String name) throws IOException {
        requireOpen();
        Long size = pending.get(name);
        if (size == null) throw new IOException("Unknown audit record");
        Files.delete(root.resolve(name));
        pending.remove(name); bytes -= size;
        syncDirectory();
    }

    private Log decode(String name, Path path) throws IOException {
        byte[] frame;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            frame = input.readNBytes(MAX_RECORD + 65);
        }
        if (frame.length < 30 || frame.length > MAX_RECORD + 64 || frame[0] != VERSION) throw new IOException("Invalid audit frame");
        byte[] plain = crypt(Cipher.DECRYPT_MODE, name, Arrays.copyOfRange(frame,1,13), Arrays.copyOfRange(frame,13,frame.length));
        try { return mapper.readValue(plain, Log.class); }
        catch (Exception failure) { throw new IOException("Invalid encrypted audit payload"); }
        finally { Arrays.fill(plain,(byte)0); }
    }

    private byte[] crypt(int mode, String name, byte[] nonce, byte[] input) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,key,new GCMParameterSpec(TAG_BITS,nonce));
            cipher.updateAAD(("mrr-audit-v1:" + name).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) { throw new IOException("Audit authentication/encryption failed"); }
    }

    private void syncDirectory() throws IOException {
        try (FileChannel channel = FileChannel.open(root, StandardOpenOption.READ)) { channel.force(true); }
    }

    private void requireOpen() throws IOException { if (closed) throw new IOException("Audit spool closed"); }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized long pendingBytes() { return bytes; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
