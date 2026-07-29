package com.nhnacademy.insightonruleengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class InsightonRuleengineApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightonRuleengineApplication.class, args);
    }
}
