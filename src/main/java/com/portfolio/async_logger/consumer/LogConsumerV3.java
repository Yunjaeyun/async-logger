package com.portfolio.async_logger.consumer;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.dto.LogDataDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * V3 BATCH
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class LogConsumerV3 {

    private final BlockingQueue<LogDataDto> logQueue;

    private final JdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE=1000;

    @PostConstruct
    public void startConsuming(){
        Thread consumerThread=new Thread(()->{
            try{
                while(!Thread.currentThread().isInterrupted()) {
                    List<LogDataDto> batchList = new ArrayList<>(BATCH_SIZE);
                    batchList.add(logQueue.take());
                    logQueue.drainTo(batchList, BATCH_SIZE - 1);

                    saveLogsInBatch(batchList);
                }

            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        });
        consumerThread.setName("LogConsumer-Thread-V3");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    public void saveLogsInBatch(List<LogDataDto> logs){
        // == 시작 시간
        long startTime = System.nanoTime();

        String sql = "INSERT INTO log(ip_address,visited_url,created_at) VALUES (?,?,?)";


        List<Object[]> batchArgs= logs.stream()
                        .map(dto->new Object[]{
                                dto.getIpAdress(),
                                dto.getVisitedUrl(),
                                Timestamp.valueOf(dto.getCreatedAt())
                        }).toList();

        jdbcTemplate.batchUpdate(sql,batchArgs);

        //==  "스톱워치" 결과 로깅
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("[V3 BATCH] {}개의 로그 처리완료. 총시간:{}ms", logs.size(), durationMs);
    }
}
