package com.nhnacademy.insightonruleengine.flow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 규칙 기반 플로우 관리 화면의 Thymeleaf 진입점을 제공한다.
 */
@Controller
public class FlowPageController {

    /**
     * 기존 REST API를 사용하는 Flow 관리 화면을 연다.
     */
    @GetMapping({"/rule/flows", "/rule/flows/"})
    public String flowManagement(
            @RequestParam(required = false) Long groupId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            Model model) {
        model.addAttribute("groupId", groupId);
        model.addAttribute("userId", userId);
        return "flow/index";
    }
}
