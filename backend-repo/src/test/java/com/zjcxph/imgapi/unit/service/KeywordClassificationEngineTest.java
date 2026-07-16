package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.RecordTypeDefinition;
import com.zjcxph.imgapi.service.KeywordClassificationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordClassificationEngineTest {

    private KeywordClassificationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new KeywordClassificationEngine(new ObjectMapper());
    }

    @Test
    void classifiesTitleKeywordWithHighConfidence() {
        KeywordClassificationEngine.Decision decision = engine.classify(
                "住院病案首页\n姓名：测试患者\n科室：内科",
                List.of(type(1, "record_home", "住院病案首页|病案首页", "", 10))
        );

        assertThat(decision.state()).isEqualTo("SUGGESTED");
        assertThat(decision.predictedBtype()).isEqualTo(1);
        assertThat(decision.confidence()).isGreaterThanOrEqualTo("0.96000");
        assertThat(decision.evidence()).contains("住院病案首页");
    }

    @Test
    void postoperativeRuleWinsWhenProgressRuleExcludesIt() {
        RecordTypeDefinition progress = type(
                2,
                "progress_note",
                "首次病程记录|病程记录",
                "术后首次病程记录",
                20
        );
        RecordTypeDefinition postoperative = type(
                4,
                "postoperative",
                "术后首次病程记录|术后病程记录",
                "",
                40
        );

        KeywordClassificationEngine.Decision decision = engine.classify(
                "术后首次病程记录\n患者术后生命体征平稳。",
                List.of(progress, postoperative)
        );

        assertThat(decision.state()).isEqualTo("SUGGESTED");
        assertThat(decision.predictedBtype()).isEqualTo(4);
    }

    @Test
    void returnsNoMatchForUnknownDocument() {
        KeywordClassificationEngine.Decision decision = engine.classify(
                "患者授权委托书\n本人同意相关事项。",
                List.of(type(8, "laboratory", "检验报告|血常规", "", 80))
        );

        assertThat(decision.state()).isEqualTo("NO_MATCH");
        assertThat(decision.predictedBtype()).isNull();
        assertThat(decision.confidence()).isNull();
    }

    private RecordTypeDefinition type(
            int btype,
            String code,
            String keywords,
            String negativeKeywords,
            int sortOrder
    ) {
        RecordTypeDefinition definition = new RecordTypeDefinition();
        definition.setBtype(btype);
        definition.setTypeCode(code);
        definition.setTypeName(code);
        definition.setKeywords(keywords);
        definition.setNegativeKeywords(negativeKeywords);
        definition.setEnabled(true);
        definition.setSortOrder(sortOrder);
        return definition;
    }
}
