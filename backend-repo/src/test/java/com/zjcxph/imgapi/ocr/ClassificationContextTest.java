package com.zjcxph.imgapi.ocr;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClassificationContextTest {
    private final ClassificationContext engine=new ClassificationContext();
    private ClassificationContext.Page page(int id,int number,Integer type,boolean independent,boolean continuation,boolean reverse) {
        return new ClassificationContext.Page(id,number,type,independent,false,continuation,reverse,true,1000,1400);
    }
    @Test void continuationRequiresItsOwnEvidenceAndAnIndependentAnchor() {
        var anchor=page(9,1,3,true,false,false);
        assertThat(engine.analyze(List.of(anchor),page(1,2,null,false,true,false)).type()).isEqualTo(3);
        assertThat(engine.analyze(List.of(anchor),page(1,2,null,false,false,false)).type()).isNull();
        assertThat(engine.analyze(List.of(page(9,1,3,false,false,false)),page(1,2,null,false,true,false)).type()).isNull();
    }
    @Test void atMostTwoContinuationPagesCanReferenceTheOriginalTitle() {
        var anchor=page(9,1,3,true,false,false);var middle=page(8,2,null,false,true,false);
        assertThat(engine.analyze(List.of(anchor,middle),page(7,3,null,false,true,false)).reference()).isEqualTo(9);
        assertThat(engine.analyze(List.of(anchor,middle,page(7,3,null,false,true,false)),page(6,4,null,false,true,false)).type()).isNull();
    }
    @Test void newTypeGapsDuplicateNumbersAndFailedPagesBreakContinuity() {
        var anchor=page(1,1,3,true,false,false);
        assertThat(engine.analyze(List.of(anchor),page(2,3,null,false,true,false)).type()).isNull();
        assertThat(engine.analyze(List.of(anchor),page(2,1,null,false,true,false)).type()).isNull();
        assertThat(engine.analyze(List.of(anchor),page(2,2,5,true,true,false)).type()).isNull();
        var failed=new ClassificationContext.Page(2,2,null,false,false,true,false,false,1000,1400);
        assertThat(engine.analyze(List.of(anchor),failed).type()).isNull();
        var heading=new ClassificationContext.Page(2,2,null,false,true,true,false,true,1000,1400);
        assertThat(engine.analyze(List.of(anchor),heading).type()).isNull();
    }
    @Test void reversePairNeedsAnExplicitMarkerAndCompatibleGeometry() {
        var anchor=page(1,1,3,true,false,false);
        assertThat(engine.analyze(List.of(anchor),page(2,2,null,false,true,true)).reversePair()).isTrue();
        assertThat(engine.analyze(List.of(anchor),page(2,2,null,false,true,false)).reversePair()).isFalse();
        var landscape=new ClassificationContext.Page(2,2,null,false,false,true,true,true,1400,1000);
        assertThat(engine.analyze(List.of(anchor),landscape).reversePair()).isFalse();
    }
}
