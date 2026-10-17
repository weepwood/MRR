package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.ClassificationEngine.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class ClassificationCatalogService {
    public record Bundle(String name,List<Rule> rules,List<Lexicon> lexicon) {}
    private final ClassificationCatalogMapper catalog; private final ClassificationMapper mapper;
    private final OcrAccess access; private final TransactionTemplate transactions;
    private final ClassificationEngine engine=new ClassificationEngine();
    public ClassificationCatalogService(ClassificationCatalogMapper catalog,ClassificationMapper mapper,OcrAccess access,TransactionTemplate transactions) {
        this.catalog=catalog;this.mapper=mapper;this.access=access;this.transactions=transactions;
    }
    public String create(AuthSession session,Bundle bundle) {
        AuthSession actor=authorize(session); validate(bundle);
        return transactions.execute(tx->{
            String version=UUID.randomUUID().toString();
            Long rules=catalog.createRules(bundle.name(),version,actor.getUsername(),actor.getId());
            Long lexicon=catalog.createLexicon(bundle.name(),version,actor.getUsername(),actor.getId());
            save(rules,lexicon,bundle); return version;
        });
    }
    public void update(AuthSession session,String version,Bundle bundle) {
        authorize(session); validate(bundle);
        transactions.executeWithoutResult(tx->{
            var current=catalog.lock(version);
            if(current==null || !"DRAFT".equals(current.status())) throw new BusinessException(409,"仅草稿允许修改");
            catalog.clearRules(version);catalog.clearLexicon(version);catalog.rename(version,bundle.name());save(catalog.ruleId(version),catalog.lexiconId(version),bundle);
        });
    }
    public List<ClassificationCatalogMapper.Version> versions(AuthSession session) {authorize(session);return catalog.versions();}
    public Bundle get(AuthSession session,String version) {
        authorize(session);
        var current=catalog.version(version);
        if(current==null) throw new BusinessException(404,"规则版本不存在");
        var rules=mapper.rules(version);var rows=mapper.lexicon(version);List<Lexicon> lexicon=new ArrayList<>();
        try {
            for(var row:rows) lexicon.add(new Lexicon(row.getTerm(),OcrProtocol.JSON.readValue(row.getAliases(),new TypeReference<>(){}),
                    OcrProtocol.JSON.readValue(row.getCommonOcrErrors(),new TypeReference<>(){})));
            return new Bundle(current.name(),rules,lexicon);
        } catch(Exception e) {throw new BusinessException(409,"词库格式无效");}
    }
    public void publish(AuthSession session,String version) {
        AuthSession actor=authorize(session);
        transactions.executeWithoutResult(tx->{
            if(!catalog.publicationLock()) throw new BusinessException(409,"有其他规则正在发布");
            var current=catalog.lock(version);
            if(current==null || !"DRAFT".equals(current.status())) throw new BusinessException(409,"仅草稿允许发布");
            Bundle bundle=get(session,version);validate(bundle);
            if(bundle.rules().isEmpty()) throw new BusinessException(400,"不能发布空规则");
            catalog.archiveRules();catalog.archiveLexicon();
            if(catalog.publishRules(version,actor.getUsername())!=1 || catalog.publishLexicon(version,actor.getUsername())!=1)
                throw new BusinessException(409,"规则与词库版本不一致");
        });
    }
    public Outcome test(AuthSession session,String version,Integer scan,OcrMapper ocr) {
        AuthSession actor=authorize(session);access.require(actor.getId(),"record:read","ocr:text-read");
        var fact=ocr.fact(scan);
        if(fact==null || !ocr.belongs(scan,fact.getArchiveId())) throw new BusinessException(404,"没有有效 OCR 结果");
        try {
            Bundle bundle=get(session,version);
            var page=new OcrProtocol.Result("test",fact.getImageWidth(),fact.getImageHeight(),
                    OcrProtocol.JSON.readValue(fact.getLinesJson(),new TypeReference<>(){}),fact.getEngineVersion());
            return engine.classify(bundle.rules(),bundle.lexicon(),page);
        } catch(Exception e) {throw new BusinessException(400,"规则测试失败");}
    }
    private AuthSession authorize(AuthSession session) {return access.require(session==null?null:session.getId(),"ocr:config");}
    private void validate(Bundle bundle) {
        if(bundle==null || bundle.name()==null || bundle.name().isBlank() || bundle.name().length()>128 || bundle.rules()==null
                || bundle.rules().size()>1000 || bundle.lexicon()==null || bundle.lexicon().size()>1000) throw new BusinessException(400,"规则集参数无效");
        try {
            bundle.rules().forEach(engine::validate);
            for(Lexicon entry:bundle.lexicon()) {
                if(entry.term()==null || entry.term().isBlank() || entry.term().length()>256 || entry.aliases()==null || entry.commonOcrErrors()==null
                        || entry.aliases().size()>30 || entry.commonOcrErrors().size()>30) throw new IllegalArgumentException();
                for(String value:java.util.stream.Stream.concat(entry.aliases().stream(),entry.commonOcrErrors().stream()).toList())
                    if(value==null || value.isBlank() || value.length()>256) throw new IllegalArgumentException();
            }
        } catch(Exception e) {throw new BusinessException(400,"分类规则或词库无效");}
    }
    private void save(Long ruleSet,Long lexiconSet,Bundle bundle) {
        bundle.rules().forEach(rule->catalog.rule(ruleSet,rule));
        try { for(var entry:bundle.lexicon()) catalog.lexicon(lexiconSet,entry.term(),OcrProtocol.JSON.writeValueAsString(entry.aliases()),OcrProtocol.JSON.writeValueAsString(entry.commonOcrErrors())); }
        catch(Exception e) {throw new BusinessException(400,"词库保存失败");}
    }
}
