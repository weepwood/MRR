package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OcrEngineProcessTest {
    @TempDir Path root;
    @Test void reusesModelProcessAndPreservesUtf8Protocol() throws Exception {
        OcrEngine engine=engine("import sys,json\nn=0\nfor raw in sys.stdin:\n n+=1\n r=json.loads(raw)\n print(json.dumps({'id':r['id'],'width':100,'height':100,'lines':[{'text':'合成第'+str(n)+'页','confidence':0.99,'box':[0,0,90,20]}],'engineVersion':'test'},ensure_ascii=False),flush=True)\n");
        try {
            assertThat(engine.recognize("test",root.resolve("page.ocr"),5,4096,()->false).text()).isEqualTo("合成第1页");
            assertThat(engine.recognize("test",root.resolve("page.ocr"),5,4096,()->false).text()).isEqualTo("合成第2页");
        } finally {engine.close();}
    }
    @Test void timeoutAndOutputFloodAreBoundedAndHaveStableFailureCodes() throws Exception {
        OcrEngine timeout=engine("import time\ntime.sleep(30)\n");
        try {assertTimeoutPreemptively(Duration.ofSeconds(5),()->assertThatThrownBy(()->timeout.recognize("test",root.resolve("page.ocr"),1,1024,()->false)).isInstanceOfSatisfying(OcrFailure.class,e->assertThat(e.code()).isEqualTo("TIMEOUT")));}
        finally {timeout.close();}
        OcrEngine flood=engine("import sys\nfor raw in sys.stdin:\n print('x'*4096,flush=True)\n");
        try {assertThatThrownBy(()->flood.recognize("test",root.resolve("page.ocr"),5,1024,()->false)).isInstanceOfSatisfying(OcrFailure.class,e->assertThat(e.code()).isEqualTo("OUTPUT_LIMIT"));}
        finally {flood.close();}
    }
    private OcrEngine engine(String script) throws Exception {
        Path executable=null;
        String configured=System.getenv("MRR_TEST_PYTHON");
        if(configured!=null) executable=Path.of(configured);
        else for(String directory:System.getenv("PATH").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            for(String name:List.of("python3","python.exe","python")) {
                Path candidate=Path.of(directory).resolve(name);if(Files.isRegularFile(candidate)) {executable=candidate;break;}
            }
            if(executable!=null) break;
        }
        assertThat(executable).as("Python is required for actual subprocess boundary tests").isNotNull();
        Files.writeString(root.resolve("manifest.json"),"{}");Files.writeString(root.resolve("fake-worker.py"),script);
        var properties=new OcrRuntimeProperties();properties.setTempDirectory(root.toString());
        var profile=new OcrRuntimeProperties.Profile();profile.setExecutable(executable.toString());profile.setScript(root.resolve("fake-worker.py").toString());
        profile.setModelDirectory(root.toString());profile.setManifestSha256(DigestUtils.sha256Hex("{}"));properties.getProfiles().put("test",profile);
        return new OcrEngine(properties);
    }
    private static void assertTimeoutPreemptively(Duration duration,org.junit.jupiter.api.function.Executable work) {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(duration,work);
    }
}
