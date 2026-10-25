package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ClassificationService {
    private final ClassificationMapper mapper;
    private final OcrMapper ocr;
    private final ClassificationEngine engine=new ClassificationEngine();
    public ClassificationService(ClassificationMapper mapper,OcrMapper ocr) { this.mapper=mapper;this.ocr=ocr; }
    /** Second pass is one bounded page per lease; never loads a whole case's OCR text. */
    public void contextOne(Job job) {
        Item item=ocr.nextContext(job.getId(),job.getContextCursor());
        if(item==null) {ocr.contextProgress(job.getId(),job.getContextCursor(),true);return;}
        try {contextPage(job,item);} catch(OcrFailure invalid) {
            ocr.contextFailure(job.getId(),item.getScanId());
        }
        ocr.contextProgress(job.getId(),item.getPageOrder(),false);
    }
    private record ContextPage(ClassificationMapper.TypeState scan,Fact fact,ClassificationContext.Page page) {}
    private void contextPage(Job job,Item item) {
        var current=mapper.scan(item.getScanId());
        if(current==null||item.getSuggestedType()!=null||item.getPageNumber()==null||item.getPageNumber()<=0
                ||!Objects.equals(item.getPageNumber(),current.getPageNumber())||item.getFailureCode()!=null
                ||!Objects.equals(current.getArchiveId(),job.getArchiveId())||!ocr.belongs(current.getId(),job.getArchiveId())) return;
        List<Integer> neighbors=ocr.neighbors(job.getArchiveId(),item.getPageNumber());
        if(neighbors.size()>=20) return;
        Map<Integer,List<ClassificationMapper.TypeState>> positions=new HashMap<>();
        for(int id:neighbors) {
            var scan=mapper.scan(id);
            if(scan!=null) positions.computeIfAbsent(scan.getPageNumber(),n->new ArrayList<>()).add(scan);
        }
        if(positions.getOrDefault(item.getPageNumber(),List.of()).size()!=1) return;
        var target=contextPage(job,current);if(target==null) return;
        List<ContextPage> preceding=new ArrayList<>();
        for(int distance=1;distance<=2;distance++) {
            var at=positions.getOrDefault(item.getPageNumber()-distance,List.of());
            if(at.size()!=1) break;
            var page=contextPage(job,at.getFirst());if(page==null) break;
            preceding.addFirst(page);if(page.page().independent()) break;
        }
        var advice=new ClassificationContext().analyze(preceding.stream().map(ContextPage::page).toList(),target.page());
        if(advice.type()==null||!Integer.valueOf(0).equals(current.getBtype())||current.getTypeVersion()!=item.getOriginalTypeVersion()) return;
        List<Evidence> evidence=preceding.stream().map(p->new Evidence(p.scan().getId(),p.scan().getTypeVersion(),p.fact().getResultVersion(),p.fact().getContentHash(),p.scan().getPageNumber())).toList();
        var outcome=new ClassificationEngine.Outcome(advice.type(),0,List.of(),List.of(),false,advice.reversePair()?"CONTEXT_REVERSE":"CONTEXT_CONTINUATION");
        suggest(job,item,target.fact(),outcome,evidence,advice.reference());
        if(advice.reversePair()&&item.getRelatedScanId()==null) {
            var previous=ocr.item(job.getId(),advice.reference());
            if(previous!=null&&previous.getRelatedScanId()==null) {
                if(ocr.pair(job.getId(),previous.getScanId(),item.getScanId())!=1
                        ||ocr.pair(job.getId(),item.getScanId(),previous.getScanId())!=1) throw new IllegalStateException("页面关联已变化");
            }
        }
    }
    private ContextPage contextPage(Job job,ClassificationMapper.TypeState scan) {
        Fact fact=ocr.fact(scan.getId());
        Item snapshot=ocr.item(job.getId(),scan.getId());
        if(snapshot!=null&&(snapshot.getFailureCode()!=null||!"SUCCEEDED".equals(snapshot.getOcrStatus())
                ||!Set.of("NO_MATCH","SUGGESTED","LOW_CONFIDENCE").contains(snapshot.getStatus())
                ||!Objects.equals(snapshot.getPageNumber(),scan.getPageNumber())
                ||fact==null||!Objects.equals(snapshot.getOcrResultVersion(),fact.getResultVersion()))) return null;
        if(snapshot==null&&Integer.valueOf(0).equals(scan.getBtype())) return null;
        if(scan.getBtype()==null||fact==null||!"SUCCEEDED".equals(fact.getStatus())||fact.getConfidence()==null||fact.getConfidence()<.90
                ||!Objects.equals(fact.getArchiveId(),job.getArchiveId())||!ocr.belongs(scan.getId(),job.getArchiveId())) return null;
        try {
            Config config=OcrProtocol.JSON.readValue(job.getConfigSnapshot(),Config.class);
            if(!Objects.equals(config.fingerprint(),fact.getModelChecksum())) return null;
            var page=page(fact);var outcome=analyze(job,page);
            List<String> headings=page.lines().stream().filter(line->line.box().get(1)<=page.height()*.25&&line.text().length()<=64)
                    .map(OcrProtocol.Line::text).toList();
            boolean continuation=headings.stream().anyMatch(t->t.matches(".*(?:续页|续表|接上页|背面|反面|[（(]续[）)]).*"));
            boolean reverse=headings.stream().anyMatch(t->t.contains("背面")||t.contains("反面"));
            boolean boundary=!outcome.excluded().isEmpty()||"CONFLICT".equals(outcome.reason())
                    ||(scan.getBtype()!=0&&!Objects.equals(scan.getBtype(),outcome.targetType()))
                    ||headings.stream().anyMatch(t->t.length()<=20&&!t.matches(".*(?:续|接上页|背面|反面).*"));
            boolean independent=outcome.autoEligible()&&(scan.getBtype()==0||Objects.equals(scan.getBtype(),outcome.targetType()));
            return new ContextPage(scan,fact,new ClassificationContext.Page(scan.getId(),scan.getPageNumber(),outcome.targetType(),independent,boundary,
                    continuation,reverse,true,fact.getImageWidth(),fact.getImageHeight()));
        } catch(OcrFailure failure) {throw failure;} catch(Exception failure) {throw new OcrFailure("INVALID_RESPONSE");}
    }
    public void classify(Job job,Item item,Fact fact) {
        try {
            var outcome=analyze(job,page(fact));
            mapper.supersede(item.getScanId(),fact.getResultVersion());
            if(outcome.targetType()!=null) suggest(job,item,fact,outcome,List.of(),null);
        } catch(OcrFailure e) {throw e;} catch(Exception e) {throw new OcrFailure("CLASSIFICATION_FAILED");}
    }
    private ClassificationEngine.Outcome analyze(Job job,OcrProtocol.Result page) throws java.io.IOException {
            var rules=mapper.rules(job.getRuleSetVersion());
            var rows=mapper.lexicon(job.getLexiconVersion());
            if(rules.size()>1000 || rows.size()>1000) throw new OcrFailure("RULE_LIMIT");
            List<ClassificationEngine.Lexicon> lexicon=new java.util.ArrayList<>();
            for(var row:rows) lexicon.add(new ClassificationEngine.Lexicon(row.getTerm(),strings(row.getAliases()),strings(row.getCommonOcrErrors())));
            return engine.classify(rules,lexicon,page);
    }
    private OcrProtocol.Result page(Fact fact) throws java.io.IOException {
        List<OcrProtocol.Line> lines=OcrProtocol.JSON.readValue(fact.getLinesJson(),new TypeReference<>(){});
        return new OcrProtocol.Result("classification",fact.getImageWidth(),fact.getImageHeight(),lines,fact.getEngineVersion());
    }
    private void suggest(Job job,Item item,Fact fact,ClassificationEngine.Outcome outcome,List<Evidence> evidence,Integer reference) {
        try {
            var scan=mapper.scan(item.getScanId());
            var suggestion=new ClassificationMapper.Suggestion();
            suggestion.setScanId(item.getScanId());suggestion.setJobId(job.getId());suggestion.setOriginalType(item.getOriginalType());
            suggestion.setOriginalTypeVersion(item.getOriginalTypeVersion());suggestion.setSuggestedType(outcome.targetType());
            suggestion.setRuleScore(outcome.score());suggestion.setOcrConfidence(fact.getConfidence());
            suggestion.setMatchedRules(OcrProtocol.JSON.writeValueAsString(outcome.matched()));suggestion.setExcludedRules(OcrProtocol.JSON.writeValueAsString(outcome.excluded()));
            suggestion.setOcrContentHash(fact.getContentHash());suggestion.setOcrResultVersion(fact.getResultVersion());
            suggestion.setRuleSetVersion(job.getRuleSetVersion());suggestion.setLexiconVersion(job.getLexiconVersion());
            suggestion.setAutoEligible(outcome.autoEligible()&&scan!=null&&scan.getTypeVersion()==item.getOriginalTypeVersion());
            suggestion.setPageNumber(item.getPageNumber());suggestion.setReferenceScanId(reference);
            suggestion.setContextEvidence(OcrProtocol.JSON.writeValueAsString(evidence));
            suggestion.setReasonCode(outcome.reason());mapper.suggest(suggestion);
            mapper.classified(job.getId(),item.getScanId(),outcome.targetType(),outcome.score(),fact.getConfidence(),
                    "SUCCEEDED".equals(fact.getStatus())?"SUGGESTED":"LOW_CONFIDENCE");
        } catch(OcrFailure e) {throw e;} catch(Exception e) {throw new OcrFailure("CLASSIFICATION_FAILED");}
    }
    private List<String> strings(String json) throws java.io.IOException {return OcrProtocol.JSON.readValue(json,new TypeReference<>(){});}
}
