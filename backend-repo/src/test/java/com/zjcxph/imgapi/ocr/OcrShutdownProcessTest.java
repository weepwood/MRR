package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.OcrJobService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrShutdownProcessTest {
    @TempDir Path root;
    @Test void disablingOcrReapsIdleProcessAndReenablingStartsFreshProcess() throws Exception {
        OcrEngine engine=engine("import os,sys,json\nfor raw in sys.stdin:\n r=json.loads(raw)\n print(json.dumps({'id':r['id'],'width':100,'height':100,'lines':[{'text':str(os.getpid()),'confidence':0.99,'box':[0,0,90,20]}],'engineVersion':'test'}),flush=True)\n");
        var jobs=mock(OcrJobService.class);
        var settings=mock(SystemSettingService.class);
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(OcrEngine.class,()->engine);
            context.registerBean(OcrRuntimeProperties.class,OcrRuntimeProperties::new);
            context.registerBean(OcrJobService.class,()->jobs);
            context.registerBean(SystemSettingService.class,()->settings);
            context.registerBean(OcrScheduler.class);context.refresh();
            long first=Long.parseLong(engine.recognize("test",root.resolve("page.ocr"),5,4096,()->false).text());
            var process=ProcessHandle.of(first).orElseThrow();
            context.getBean(OcrScheduler.class).dispatch();
            process.onExit().get(2,TimeUnit.SECONDS);
            assertThat(process.isAlive()).isFalse();
            verify(jobs,never()).runOne();
            when(jobs.enabled()).thenReturn(true);
            context.getBean(OcrScheduler.class).dispatch();
            long next=Long.parseLong(engine.recognize("test",root.resolve("page.ocr"),5,4096,()->false).text());
            assertThat(next).isNotEqualTo(first);
        } finally {engine.close();}
    }
    @Test void resetDoesNotWaitForCancellationLookupAndFencesLateProcessRegistration() throws Exception {
        OcrEngine engine=engine("import time\ntime.sleep(30)\n");
        var entered=new CountDownLatch(1);var resume=new CountDownLatch(1);
        var caller=Executors.newSingleThreadExecutor();
        var result=caller.submit(()->engine.recognize("test",root.resolve("page.ocr"),5,4096,()->{
            entered.countDown();
            try {if(!resume.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("synthetic lookup timeout");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();return true;}
            return false;
        }));
        try {
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1),engine::reset);
            resume.countDown();
            assertThatThrownBy(()->result.get(2,TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OcrFailure.class)
                    .satisfies(failure->assertThat(((OcrFailure)failure.getCause()).code()).isEqualTo("CANCELLED"));
        } finally {resume.countDown();caller.shutdownNow();engine.close();}
    }
    private OcrEngine engine(String script) throws Exception {
        Path executable=null;String configured=System.getenv("MRR_TEST_PYTHON");
        if(configured!=null) executable=Path.of(configured);
        else for(String directory:System.getenv("PATH").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            for(String name:java.util.List.of("python3","python.exe","python")) {
                Path candidate=Path.of(directory).resolve(name);if(Files.isRegularFile(candidate)){executable=candidate;break;}
            }
            if(executable!=null) break;
        }
        assertThat(executable).as("Python is required for real process shutdown tests").isNotNull();
        Files.writeString(root.resolve("manifest.json"),"{}");Files.writeString(root.resolve("fake-worker.py"),script);
        var properties=new OcrRuntimeProperties();properties.setTempDirectory(root.toString());
        var profile=new OcrRuntimeProperties.Profile();profile.setExecutable(executable.toString());
        profile.setScript(root.resolve("fake-worker.py").toString());profile.setModelDirectory(root.toString());
        profile.setManifestSha256(DigestUtils.sha256Hex("{}"));properties.getProfiles().put("test",profile);
        return new OcrEngine(properties);
    }
}
