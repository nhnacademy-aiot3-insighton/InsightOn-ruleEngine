package com.nhnacademy.insightonruleengine.flow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FlowPageController.class)
class FlowPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기본 주소에는 규칙 기반 플로우 관리 화면을 노출하지 않는다")
    void homeNotFoundTest() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("규칙 기반 플로우 관리 화면을 Thymeleaf로 렌더링한다")
    void flowManagementPageTest() throws Exception {
        mockMvc.perform(get("/rule/flows"))
                .andExpect(status().isOk())
                .andExpect(view().name("flow/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("규칙 기반 플로우")));
    }
}
