package com.nhnacademy.insightonruleengine.flow.domain.node.parser;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NodeParamsParserTest {

    private static NodeParamsParser parser;

    @BeforeAll
    static void setUp() {
        parser = new NodeParamsParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @AfterAll
    static void tearDown() {
        // ValidatorFactory는 parser가 직접 소유하지 않으므로 테스트에서는 별도 종료가 필요하지 않습니다.
    }

    @Test
    void reusesParsedImmutableParams() {
        ObjectNode configuration = new ObjectMapper().createObjectNode().put("sensorId", 10L);

        SensorParams first = parser.parse(NodeType.SENSOR, configuration);
        SensorParams second = parser.parse(NodeType.SENSOR, configuration.deepCopy());

        assertSame(first, second);
    }
}
