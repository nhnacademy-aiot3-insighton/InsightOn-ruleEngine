package com.nhnacademy.insightonruleengine.node.domain.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeParamsParserTest {

    private ValidatorFactory validatorFactory;
    private NodeParamsParser parser;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        parser = new NodeParamsParser(new ObjectMapper(), validatorFactory.getValidator());
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("NodeType에 맞는 Params로 configuration을 파싱한다")
    void parseThresholdParams() {
        ObjectNode configuration = new ObjectMapper().createObjectNode()
                .put("expression", "#temperature > 30");

        ThresholdParams params = parser.parse(NodeType.THRESHOLD, configuration);

        assertEquals("#temperature > 30", params.expression());
    }

    @Test
    @DisplayName("Params validation 실패 시 예외가 발생한다")
    void rejectInvalidParams() {
        ObjectNode configuration = new ObjectMapper().createObjectNode()
                .put("severity", "WARN")
                .put("message", " ");

        assertThrows(
                ConstraintViolationException.class,
                () -> parser.<AlertParams>parse(NodeType.ALERT, configuration)
        );
    }
}
