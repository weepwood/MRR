package com.zjcxph.imgapi.ocr;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClassificationEngineTest {
    private final ClassificationEngine engine = new ClassificationEngine();
    @Test void onlyExactIndependentTitleCanQualifyForAutomaticApplication() {
        var rule=rule(3,"手术记录","TITLE","EXACT");
        var title=engine.classify(List.of(rule),List.of(),page("手术记录",0.99,10));
        assertThat(title.targetType()).isEqualTo(3); assertThat(title.autoEligible()).isTrue();
        var body=engine.classify(List.of(rule),List.of(),page("既往手术记录已查阅",0.99,500));
        assertThat(body.targetType()).isNull(); assertThat(body.autoEligible()).isFalse();
    }
    @Test void conflictingOrWeakTitlesNeverAutomaticallyApply() {
        var conflict=new OcrProtocol.Result("x",1000,1000,List.of(
                new OcrProtocol.Line("手术记录",.99,List.of(0.,10.,500.,40.)),
                new OcrProtocol.Line("护理记录",.99,List.of(0.,50.,500.,80.))),"test");
        assertThat(engine.classify(List.of(rule(3,"手术记录","TITLE","EXACT"),rule(5,"护理记录","TITLE","EXACT")),List.of(),conflict).autoEligible()).isFalse();
        assertThat(engine.classify(List.of(rule(3,"手术记录","TITLE","EXACT")),List.of(),page("手术记录",.95,10)).autoEligible()).isFalse();
    }
    @Test void correctionsCanSuggestButNeverAutomaticallyApply() {
        var correction=new ClassificationEngine.Lexicon("手术记录",List.of(),List.of("手木记录"));
        var outcome=engine.classify(List.of(rule(3,"手术记录","TITLE","EXACT")),List.of(correction),page("手木记录",.99,10));
        assertThat(outcome.targetType()).isEqualTo(3); assertThat(outcome.autoEligible()).isFalse();
    }
    @Test void noMatchDoesNotBecomeOtherAndSupportsNewbornAndDelivery() {
        assertThat(engine.classify(List.of(rule(14,"其它","TITLE","EXACT")),List.of(),page("未知页面",.99,10)).targetType()).isNull();
        for(int type:List.of(11,15)) assertThat(engine.classify(List.of(rule(type,"合成标题","TITLE","EXACT")),List.of(),page("合成标题",.99,10)).targetType()).isEqualTo(type);
    }
    @Test void rejectsInvalidTypesAndUnboundedRegexSyntax() {
        assertThatThrownBy(()->engine.validate(rule(16,"x","TITLE","EXACT"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->engine.validate(rule(1,"(x)\\1","ANY","REGEX"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateLexiconTermsAggregateAliasesForExactMatch() {
        var first=new ClassificationEngine.Lexicon("手术记录",List.of("手术记录单页"),List.of());
        var second=new ClassificationEngine.Lexicon("手术 记录",List.of("手术纪录"),List.of());
        var rules=List.of(rule(3,"手术记录","ANY","EXACT"));
        assertThat(engine.classify(rules,List.of(first,second),page("手术纪录",.99,10)).targetType()).isEqualTo(3);
        assertThat(engine.classify(rules,List.of(first,second),page("手术记录单页",.99,10)).targetType()).isEqualTo(3);
    }
    @Test void lexiconCorrectionsChainInLexiconOrder() {
        var report=new ClassificationEngine.Lexicon("检验报告",List.of(),List.of("检验报呿"));
        var sheet=new ClassificationEngine.Lexicon("检验报告单",List.of(),List.of("检验报告"));
        var forward=engine.classify(List.of(rule(4,"检验报告单","ANY","EXACT")),List.of(report,sheet),page("检验报呿",.99,10));
        assertThat(forward.targetType()).isEqualTo(4);
        var reversed=engine.classify(List.of(rule(4,"检验报告单","ANY","EXACT")),List.of(sheet,report),page("检验报呿",.99,10));
        assertThat(reversed.targetType()).isNull();
    }
    @Test void regexRulesMatchRawKeywordAgainstNormalizedText() {
        assertThat(engine.classify(List.of(rule(5,"手.*录","ANY","REGEX")),List.of(),page("手 术记录",.99,10)).targetType()).isEqualTo(5);
    }
    @Test void keywordAndTextNormalizationIgnoresWhitespace() {
        assertThat(engine.classify(List.of(rule(3,"手 术 记 录","ANY","EXACT")),List.of(),page("手术记录",.99,10)).targetType()).isEqualTo(3);
    }
    @Test void largeRuleLexiconAndPageClassificationMeetsInteractiveDeadline() {
        List<ClassificationEngine.Rule> rules=new java.util.ArrayList<>();
        for(int i=0;i<600;i++) rules.add(rule(1000L+i,1+(i%15),String.format("规则关键词%03d",i),"ANY","CONTAINS"));
        List<ClassificationEngine.Lexicon> lexicon=new java.util.ArrayList<>();
        for(int i=0;i<150;i++) lexicon.add(new ClassificationEngine.Lexicon(String.format("词典词条%03d",i),List.of(),List.of(String.format("常见错别字%03d",i))));
        List<OcrProtocol.Line> lines=new java.util.ArrayList<>();
        for(int i=0;i<150;i++) lines.add(new OcrProtocol.Line(String.format("页面正文文本第%03d行",i),.9,List.of(0.,(double)i,500.,(double)i+20)));
        var bulk=new OcrProtocol.Result("bulk",1000,1000,lines,"test");
        var outcome=org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),()->engine.classify(rules,lexicon,bulk));
        assertThat(outcome.targetType()).isNull();
    }
    private ClassificationEngine.Rule rule(int type,String keyword,String scope,String mode) {
        return rule(1L,type,keyword,scope,mode);
    }
    private ClassificationEngine.Rule rule(long id,int type,String keyword,String scope,String mode) {
        return new ClassificationEngine.Rule(id,type,keyword,scope,mode,1,false,false,100,true);
    }
    private OcrProtocol.Result page(String text,double score,double y) {
        return new OcrProtocol.Result("x",1000,1000,List.of(new OcrProtocol.Line(text,score,List.of(0.,y,500.,y+30))),"test");
    }
}
