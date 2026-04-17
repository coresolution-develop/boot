package com.coresolution.pe.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {
    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}