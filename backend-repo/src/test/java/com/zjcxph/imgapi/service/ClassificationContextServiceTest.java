package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Database atomicity is covered by OcrContextPostgresqlIT; these tests isolate evidence decisions. */
class ClassificationContextServiceTest {
    private final ClassificationMapper catalog=mock(ClassificationMapper.class);
    private final OcrMapper queue=mock(OcrMapper.class);
    private final ClassificationService service=new ClassificationService(catalog,queue);
    private final Map<Integer,ClassificationMapper.TypeState> scans=new HashMap<>();
    private final Map<Integer,Fact> facts=new HashMap<>();
    private final Map<Integer,Item> items=new HashMap<>();
    private final Job job=new Job();
    @BeforeEach void setup() throws Exception {
        job.setId("synthetic-job");job.setArchiveId(1L);job.setRuleSetVersion("synthetic-rules");job.setLexiconVersion("none");
        job.setConfigSnapshot(OcrProtocol.JSON.writeValueAsString(new Config("synthetic","b".repeat(64),30,4194304,.7)));
        add(11,1,"手术记录");add(12,2,"续页");
        when(catalog.scan(anyInt())).thenAnswer(c->scans.get(c.getArgument(0)));
        when(queue.fact(anyInt())).thenAnswer(c->facts.get(c.getArgument(0)));
        when(queue.item(eq(job.getId()),anyInt())).thenAnswer(c->items.get(c.getArgument(1)));
        when(queue.belongs(anyInt(),eq(1L))).thenReturn(true);
        when(queue.nextContext(job.getId(),0)).thenReturn(items.get(12));
        when(queue.neighbors(1L,2)).thenReturn(List.of(11,12));
        when(catalog.rules("synthetic-rules")).thenReturn(List.of(new ClassificationEngine.Rule(1L,3,"手术记录","TITLE","EXACT",1,false,false,100,true)));
        when(catalog.lexicon("none")).thenReturn(List.of());
        when(queue.pair(anyString(),anyInt(),anyInt())).thenReturn(1);
    }
    private void add(int id,int number,String title) throws Exception {
        var scan=new ClassificationMapper.TypeState();scan.setId(id);scan.setArchiveId(1L);scan.setBtype(0);scan.setUploadflag(1);scan.setPageNumber(number);scans.put(id,scan);
        Fact fact=new Fact();fact.setScanId(id);fact.setArchiveId(1L);fact.setStatus("SUCCEEDED");fact.setConfidence(.99);fact.setModelChecksum("b".repeat(64));
        fact.setContentHash("a".repeat(64));fact.setResultVersion("synthetic-result-"+id);fact.setImageWidth(1000);fact.setImageHeight(1400);fact.setOcrText(title);
        fact.setLinesJson(OcrProtocol.JSON.writeValueAsString(List.of(new OcrProtocol.Line(title,.99,List.of(0.,10.,500.,60.)))));facts.put(id,fact);
        Item item=new Item();item.setJobId(job.getId());item.setScanId(id);item.setPageNumber(number);item.setPageOrder(number);
        item.setStatus("NO_MATCH");item.setOcrStatus("SUCCEEDED");item.setOcrResultVersion(fact.getResultVersion());items.put(id,item);
    }
    private ClassificationMapper.Suggestion advice() {
        var captured=ArgumentCaptor.forClass(ClassificationMapper.Suggestion.class);verify(catalog).suggest(captured.capture());return captured.getValue();
    }
    @Test void continuationBindsOriginalVersionsAndNeverAcquiresAutomaticEligibility() {
        service.contextOne(job);var suggestion=advice();
        assertThat(suggestion.getSuggestedType()).isEqualTo(3);assertThat(suggestion.getReferenceScanId()).isEqualTo(11);
        assertThat(suggestion.getReasonCode()).isEqualTo("CONTEXT_CONTINUATION");assertThat(suggestion.isAutoEligible()).isFalse();
        assertThat(suggestion.getContextEvidence()).contains("synthetic-result-11");
        assertThat(suggestion.getOcrResultVersion()).isEqualTo("synthetic-result-12");
        verify(queue).contextProgress(job.getId(),2,false);verify(queue,never()).pair(anyString(),anyInt(),anyInt());
        verify(catalog,never()).change(anyInt(),anyInt(),anyLong());assertThat(facts.get(12).getOcrText()).isEqualTo("续页");
    }
    @Test void reverseMarkerProducesAReviewableSymmetricRelation() throws Exception {
        add(12,2,"续页（背面）");when(queue.nextContext(job.getId(),0)).thenReturn(items.get(12));
        service.contextOne(job);assertThat(advice().getReasonCode()).isEqualTo("CONTEXT_REVERSE");
        verify(queue).pair(job.getId(),11,12);verify(queue).pair(job.getId(),12,11);
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","CANCELLED","PROCESSING","stale-version","moved-page","failure-code","ocr-failed","not-in-manifest","wrong-model","stale-source","low-confidence","wrong-archive","formal-conflict"})
    void unusablePreviousPageCannotBridgeToAnOlderSuccessfulFact(String condition) {
        switch(condition) {
            case "stale-version" -> items.get(11).setOcrResultVersion("superseded");
            case "moved-page" -> scans.get(11).setPageNumber(0);
            case "failure-code" -> items.get(11).setFailureCode("TIMEOUT");
            case "ocr-failed" -> items.get(11).setOcrStatus("FAILED");
            case "not-in-manifest" -> items.remove(11);
            case "wrong-model" -> facts.get(11).setModelChecksum("c".repeat(64));
            case "stale-source" -> facts.get(11).setStatus("STALE");
            case "low-confidence" -> facts.get(11).setConfidence(.7);
            case "wrong-archive" -> facts.get(11).setArchiveId(2L);
            case "formal-conflict" -> scans.get(11).setBtype(5);
            default -> items.get(11).setStatus(condition);
        }
        service.contextOne(job);verify(catalog,never()).suggest(any());verify(queue).contextProgress(job.getId(),2,false);
        assertThat(facts.get(11).getOcrText()).isEqualTo("手术记录");
    }
    @ParameterizedTest @ValueSource(strings={"page-order","type-version","already-classified","wrong-archive","deleted","ocr-version","model","low-confidence","source-status","missing-page","missing-scan","missing-fact"})
    void changedTargetIsLeftForManualHandlingWithoutMutatingItsType(String condition) {
        switch(condition) {
            case "page-order" -> scans.get(12).setPageNumber(3);
            case "type-version" -> scans.get(12).setTypeVersion(1);
            case "already-classified" -> scans.get(12).setBtype(5);
            case "wrong-archive" -> scans.get(12).setArchiveId(2L);
            case "deleted" -> when(queue.belongs(12,1L)).thenReturn(false);
            case "ocr-version" -> facts.get(12).setResultVersion("replaced");
            case "model" -> facts.get(12).setModelChecksum("c".repeat(64));
            case "low-confidence" -> facts.get(12).setConfidence(.7);
            case "source-status" -> facts.get(12).setStatus("STALE");
            case "missing-page" -> items.get(12).setPageNumber(null);
            case "missing-scan" -> scans.remove(12);
            case "missing-fact" -> facts.remove(12);
        }
        service.contextOne(job);verify(catalog,never()).suggest(any());verify(catalog,never()).change(anyInt(),anyInt(),anyLong());
    }
    @Test void duplicateNumbersBlockContextEvenWhenTheOtherImageHasNoOcr() throws Exception {
        add(13,2,"合成重复页");facts.remove(13);when(queue.neighbors(1L,2)).thenReturn(List.of(11,12,13));
        service.contextOne(job);verify(catalog,never()).suggest(any());
    }
    @Test void preclassifiedPageCanAnchorUsingExistingOcrWithoutReprocessingIt() {
        scans.get(11).setBtype(3);items.remove(11);service.contextOne(job);
        assertThat(advice().getReferenceScanId()).isEqualTo(11);verify(queue,never()).saveFact(any());
    }
    @Test void malformedOcrPreservesFactAndAdvancesAnExplicitFailureCheckpoint() {
        facts.get(12).setLinesJson("not-json");service.contextOne(job);
        verify(queue).contextFailure(job.getId(),12);verify(queue).contextProgress(job.getId(),2,false);
        verify(queue,never()).saveFact(any());verify(catalog,never()).suggest(any());
    }
    @Test void exhaustionCompletesOnlyTheContextPhase() {
        when(queue.nextContext(job.getId(),0)).thenReturn(null);service.contextOne(job);
        verify(queue).contextProgress(job.getId(),0,true);verify(catalog,never()).suggest(any());
    }
    @Test void directTitleStillUsesTheOriginalSinglePageEligibilityRules() {
        service.classify(job,items.get(11),facts.get(11));var suggestion=advice();
        assertThat(suggestion.isAutoEligible()).isTrue();assertThat(suggestion.getReferenceScanId()).isNull();
        assertThat(suggestion.getContextEvidence()).isEqualTo("[]");
    }
}
