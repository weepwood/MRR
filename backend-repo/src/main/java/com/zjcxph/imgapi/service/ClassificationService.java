package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ClassificationService {
    private final ClassificationMapper mapper;
    private final OcrMapper ocr;
    private final ClassificationEngine engine=new ClassificationEngine();
    public ClassificationService(ClassificationMapper mapper,OcrMapper ocr) { this.mapper=mapper;this.ocr=ocr; }
    public void classify(Job job,Item item,Fact fact) {
        try {
            var rules=mapper.rules(job.getRuleSetVersion());
            var rows=mapper.lexicon(job.getLexiconVersion());
            if(rules.size()>1000 || rows.size()>1000) throw new OcrFailure("RULE_LIMIT");
            List<ClassificationEngine.Lexicon> lexicon=new java.util.ArrayList<>();
            for(var row:rows) lexicon.add(new ClassificationEngine.Lexicon(row.getTerm(),strings(row.getAliases()),strings(row.getCommonOcrErrors())));
            List<OcrProtocol.Line> lines=OcrProtocol.JSON.readValue(fact.getLinesJson(),new TypeReference<>(){});
            var page=new OcrProtocol.Result("classification",fact.getImageWidth(),fact.getImageHeight(),lines,fact.getEngineVersion());
            var outcome=engine.classify(rules,lexicon,page);
            mapper.supersede(item.getScanId(),fact.getResultVersion());
            if(outcome.targetType()==null) return;
            var scan=mapper.scan(item.getScanId());
            var suggestion=new ClassificationMapper.Suggestion();
            suggestion.setScanId(item.getScanId());suggestion.setJobId(job.getId());suggestion.setOriginalType(item.getOriginalType());
            suggestion.setOriginalTypeVersion(item.getOriginalTypeVersion());suggestion.setSuggestedType(outcome.targetType());
            suggestion.setRuleScore(outcome.score());suggestion.setOcrConfidence(fact.getConfidence());
            suggestion.setMatchedRules(OcrProtocol.JSON.writeValueAsString(outcome.matched()));suggestion.setExcludedRules(OcrProtocol.JSON.writeValueAsString(outcome.excluded()));
            suggestion.setOcrContentHash(fact.getContentHash());suggestion.setOcrResultVersion(fact.getResultVersion());
            suggestion.setRuleSetVersion(job.getRuleSetVersion());suggestion.setLexiconVersion(job.getLexiconVersion());
            suggestion.setAutoEligible(outcome.autoEligible()&&scan!=null&&scan.getTypeVersion()==item.getOriginalTypeVersion());
            suggestion.setReasonCode(outcome.reason());mapper.suggest(suggestion);
            mapper.classified(job.getId(),item.getScanId(),outcome.targetType(),outcome.score(),fact.getConfidence(),
                    "SUCCEEDED".equals(fact.getStatus())?"SUGGESTED":"LOW_CONFIDENCE");
        } catch(OcrFailure e) {throw e;} catch(Exception e) {throw new OcrFailure("CLASSIFICATION_FAILED");}
    }
    private List<String> strings(String json) throws java.io.IOException {return OcrProtocol.JSON.readValue(json,new TypeReference<>(){});}
}
