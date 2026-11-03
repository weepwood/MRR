package com.zjcxph.imgapi.unit.mapper;

import com.zjcxph.imgapi.mapper.ScanMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScanLegacyQueryTimeoutTest {
    @ParameterizedTest
    @ValueSource(strings = {"findAll", "findAllWithPagination", "findByCondition",
            "findByConditionWithPagination", "countByCondition"})
    void legacyReadsHaveAnExplicitStatementBudget(String method) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/ScanMapper.xml";
        try (var input = ScanMapper.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.getMappedStatement(ScanMapper.class.getName() + "." + method).getTimeout())
                .as("%s must not depend on an unlimited driver/global default", method)
                .isEqualTo(3);
    }
}
