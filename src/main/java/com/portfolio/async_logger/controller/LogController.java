package com.portfolio.async_logger.controller;

import com.portfolio.async_logger.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogController {

    @Autowired
    private LogService logService;

    @GetMapping("/visit/v1")
    public ResponseEntity<String> visitV1(){
        logService.saveLog("127.0.0.1","/visit/v1");

        return ResponseEntity.ok("V1 Page Loaded!");
    }

}
