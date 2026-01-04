package com.yy.yyapigateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 阿狸
 * @date 2026-01-04
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}
