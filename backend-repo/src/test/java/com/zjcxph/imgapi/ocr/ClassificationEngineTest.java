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
    private ClassificationEngine.Rule rule(int type,String keyword,String scope,String mode) {
        return new ClassificationEngine.Rule(1L,type,keyword,scope,mode,1,false,false,100,true);
    }
    private OcrProtocol.Result page(String text,double score,double y) {
        return new OcrProtocol.Result("x",1000,1000,List.of(new OcrProtocol.Line(text,score,List.of(0.,y,500.,y+30))),"test");
    }
}
