package com.portfolio.async_logger.controller;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.dto.LogDataDto;
import com.portfolio.async_logger.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.concurrent.BlockingQueue;

@Controller
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final BlockingQueue<LogDataDto> logQueue;

    /* error로 인해 서버가 터지는 일반적인 상황*/
    @GetMapping("/visit/v1")
    public ResponseEntity<String> visitV1(){
        logService.saveLog("127.0.0.1","/visit/v1");

        return ResponseEntity.ok("V1 Page Loaded!");
    }

    @GetMapping("visit/v2")
    public ResponseEntity<String> visitV2(){
        LogDataDto logDto =  LogDataDto.of("127.0.0.1", "/visit/v2");

        logQueue.offer(logDto);
        return ResponseEntity.ok("V2 page Loaded Instantly!");
    }

}
