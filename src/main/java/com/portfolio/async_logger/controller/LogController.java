package com.portfolio.async_logger.controller;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.dto.LogDataDto;
import com.portfolio.async_logger.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;

@Controller
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final BlockingQueue<LogDataDto> logQueue;
    private final BlockingQueue<String> logStringQueue;

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


    @GetMapping("visit/v3")
    public ResponseEntity<String> visitV3(){
        return visitV2();
    }

    /**
     * 쓰레기(DTO)를 만들지 않고 String을 큐에 삽입한다.
     */
    @GetMapping("visit/v4")
    public ResponseEntity<String> visitV4(){
        String ipAddress = "127.0.0.1";
        String visitedUrl = "";
        String createdAt = LocalDateTime.now().toString();

        String logString = ipAddress + "," + visitedUrl + "," + createdAt;

        boolean offered = logStringQueue.offer(logString);

        return ResponseEntity.ok("V4(GC-Turned) - ok ");

    }



    @GetMapping("/monitor")
    public ResponseEntity<String> monitorQueue(){
        int currentQueueSize= logQueue.size();
        return ResponseEntity.ok("Current Queue Size: "+currentQueueSize );
    }
}
