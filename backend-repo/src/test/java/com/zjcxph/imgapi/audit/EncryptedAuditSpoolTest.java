package com.zjcxph.imgapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@EnabledOnOs({OS.MAC, OS.LINUX})
class EncryptedAuditSpoolTest {
    @TempDir Path temp;
    private static final byte[] KEY = new byte[32]; // synthetic fixture only
    private static final String MARKER="SYNTHETIC_PRIVATE_AUDIT_BODY";
    private static final ObjectMapper JSON=new ObjectMapper();

    static Log log(String id) {
        var row=new Log();row.setRequestId(id);row.setAccessTime(new Date(1760000000000L));
        row.setUsername("synthetic");row.setRequestUri("/synthetic");row.setRequestBody(MARKER);
        row.setMethod("GET");row.setResponseStatus("200");
        return row;
    }
    private Path directory() throws Exception {
        return Files.createDirectory(temp.toRealPath().resolve("spool"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    private static EncryptedAuditSpool open(Path p,int max,byte[] key) throws Exception {
        return new EncryptedAuditSpool(p,key,max,65536,JSON);
    }
    @Test void encryptedAdmissionSurvivesReopenAndCapacityDoesNotEvict() throws Exception {
        Path p=directory();
        try(var spool=open(p,2,KEY)) {
            spool.append(log("first"));spool.append(log("second"));
            assertThatThrownBy(()->spool.append(log("third"))).isInstanceOf(EncryptedAuditSpool.CapacityException.class);
            assertThat(spool.pendingCount()).isEqualTo(2);
            try(var files=Files.list(p)) {
                for(var f:files.filter(x->x.toString().endsWith(".pending")).toList()) {
                    assertThat(Files.getPosixFilePermissions(f)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
                    assertThat(new String(Files.readAllBytes(f),StandardCharsets.ISO_8859_1)).doesNotContain(MARKER,"first","second");
                }
            }
            assertThatThrownBy(()->open(p,2,KEY)).isInstanceOf(java.io.IOException.class);
        }
        byte[] wrong=new byte[32];wrong[0]=1;
        assertThatThrownBy(()->open(p,2,wrong)).isInstanceOf(java.io.IOException.class);
        try(var spool=open(p,2,KEY)) {
            var rows=spool.peek(50);
            assertThat(rows).extracting(x->x.log().getRequestId()).containsExactlyInAnyOrder("first","second");
            assertThat(rows).allSatisfy(x->assertThat(x.log().getRequestBody()).isEqualTo(MARKER));
            spool.acknowledge(rows.getFirst().name());
        }
        try(var spool=open(p,2,KEY)) {assertThat(spool.pendingCount()).isEqualTo(1);}
    }
    @Test void rejectsInsecureDirectoryAndTamperedCiphertext() throws Exception {
        Path p=directory();
        Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThatThrownBy(()->open(p,10,KEY)).isInstanceOf(java.io.IOException.class);
        Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwx------"));
        String file;
        try(var spool=open(p,10,KEY)) {spool.append(log("tampered"));file=spool.peek(1).getFirst().name();}
        Path f=p.resolve(file);byte[] data=Files.readAllBytes(f);data[data.length-1]^=1;Files.write(f,data);
        assertThatThrownBy(()->open(p,10,KEY)).isInstanceOf(java.io.IOException.class);
        assertThat(f).exists();
    }
    @Test void rejectsByteBudgetAndOversizeWithoutDeletingAcceptedRecords() throws Exception {
        try(var spool=open(directory(),100,KEY)) {
            var row=log("large");row.setRequestBody("x".repeat(40000));spool.append(row);
            var next=log("next");next.setRequestBody("y".repeat(40000));
            assertThatThrownBy(()->spool.append(next)).isInstanceOf(EncryptedAuditSpool.CapacityException.class);
            next.setRequestBody("z".repeat(70000));
            assertThatThrownBy(()->spool.append(next)).isInstanceOf(EncryptedAuditSpool.CapacityException.class);
            assertThat(spool.peek(1).getFirst().log().getRequestId()).isEqualTo("large");
        }
    }
    @Test void sealedGroupUsesOneFileForceAndRecoversAllOrNone() throws Exception {
        Path p=directory();
        try(var spool=open(p,5,KEY)){
            spool.append(log("legacy"));
            spool.appendGroup(List.of(log("group-a"),log("group-b"),log("group-c")));
            assertThat(spool.pendingCount()).isEqualTo(4);
            assertThat(spool.fileForces()).isEqualTo(2);
            assertThat(spool.publishedSegments()).isEqualTo(2);
            assertThatThrownBy(()->spool.appendGroup(List.of(log("overflow-a"),log("overflow-b")))).isInstanceOf(EncryptedAuditSpool.CapacityException.class);
        }
        try(var spool=open(p,5,KEY)){
            var rows=spool.peek(50);
            assertThat(rows).extracting(x->x.log().getRequestId()).containsExactlyInAnyOrder("legacy","group-a","group-b","group-c");
            assertThat(rows.stream().map(EncryptedAuditSpool.Entry::name).distinct().count()).isEqualTo(2);
            spool.acknowledgeSegments(rows.stream().map(EncryptedAuditSpool.Entry::name).toList());
            assertThat(spool.pendingCount()).isZero();
        }
    }
    @Test void concurrentAdmissionWaitsForDurableGroupAndReleasesAllCallers() throws Exception {
        Path p=directory();
        try(var spool=open(p,100,KEY);var admission=new GroupedAuditAdmission(spool)){
            var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
            var gate=new java.util.concurrent.CountDownLatch(1);
            try{
                var futures=new ArrayList<java.util.concurrent.Future<?>>();
                for(int i=0;i<32;i++){int id=i;futures.add(pool.submit(()->{gate.await();admission.append(log("parallel-"+id));return null;}));}
                gate.countDown();
                for(var f:futures)f.get(10,TimeUnit.SECONDS);
                assertThat(spool.pendingCount()).isEqualTo(32);
                assertThat(spool.fileForces()).isLessThan(32);
                System.out.println("MRR_GROUP_FORCE records=32 file_forces="+spool.fileForces()+" bytes="+spool.publishedBytes());
            }finally{pool.shutdownNow();}
        }
        try(var spool=open(p,100,KEY)){assertThat(spool.peek(50)).hasSize(32);}
    }

    @Test void rotationRequiresOldKeyUntilAllOldSegmentsAreAcknowledged() throws Exception {
        Path p=directory();byte[] old=new byte[32],next=new byte[32];old[0]=11;next[0]=12;
        try(var spool=open(p,10,KEY)){spool.append(log("legacy"));}
        try(var spool=new EncryptedAuditSpool(p,KEY,Map.of("old",old),"old",10,65536,JSON)){
            spool.appendGroup(List.of(log("old-a"),log("old-b")));
        }
        assertThatThrownBy(()->new EncryptedAuditSpool(p,KEY,Map.of("next",next),"next",10,65536,JSON)).isInstanceOf(java.io.IOException.class);
        try(var spool=new EncryptedAuditSpool(p,KEY,Map.of("old",old,"next",next),"next",10,65536,JSON)){
            spool.append(log("next"));
            var rows=spool.peek(50);
            assertThat(rows).extracting(x->x.log().getRequestId()).containsExactlyInAnyOrder("legacy","old-a","old-b","next");
            spool.acknowledgeSegments(rows.stream().filter(x->!x.log().getRequestId().equals("next")).map(EncryptedAuditSpool.Entry::name).toList());
        }
        try(var spool=new EncryptedAuditSpool(p,KEY,Map.of("next",next),"next",10,65536,JSON)){
            assertThat(spool.peek(50)).extracting(x->x.log().getRequestId()).containsExactly("next");
        }
    }

    @Test void heldSegmentsSurviveRestartAndRemainInCapacityWithoutBlockingNextPending()throws Exception{
        Path p=directory();
        try(var spool=open(p,3,KEY)){
            spool.appendGroup(List.of(log("conflict-a"),log("conflict-b")));
            spool.hold(spool.peek(50).getFirst().name());
            spool.append(log("healthy"));
            assertThat(spool.heldCount()).isEqualTo(2);
            assertThat(spool.pendingCount()).isEqualTo(3);
            assertThatThrownBy(()->spool.append(log("full"))).isInstanceOf(EncryptedAuditSpool.CapacityException.class);
        }
        try(var spool=open(p,3,KEY)){
            assertThat(spool.heldCount()).isEqualTo(2);
            var rows=spool.peek(50);
            assertThat(rows).extracting(x->x.log().getRequestId()).containsExactly("healthy");
            spool.acknowledge(rows.getFirst().name());
            assertThat(spool.pendingCount()).isEqualTo(2);
        }
    }

    @Test void actualChildJvmKillReleasesLockAndRecoversPublishedRecord() throws Exception {
        Path p=directory();
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        var child=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),Child.class.getName(),p.toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("child.log").toFile()).start();
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<deadline && child.isAlive() && !Files.readString(temp.resolve("child.log")).contains("PUBLISHED"))
                Thread.sleep(20);
            assertThat(Files.readString(temp.resolve("child.log"))).contains("PUBLISHED");
            assertThatThrownBy(()->open(p,10,KEY)).isInstanceOf(java.io.IOException.class);
            child.destroyForcibly();
            assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();
            try(var spool=open(p,10,KEY)) {
                assertThat(spool.peek(16)).extracting(x->x.log().getRequestId()).containsExactly("child-published","child-second");
            }
            System.out.println("MRR_SPOOL_KILL child forcibly terminated, encrypted published record recovered");
        } finally {if(child.isAlive()) child.destroyForcibly();}
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            try(var spool=open(Path.of(args[0]),10,KEY)) {
                spool.appendGroup(List.of(log("child-published"),log("child-second")));
                System.out.println("PUBLISHED");System.out.flush();
                Thread.sleep(60000);
            }
        }
    }
}
