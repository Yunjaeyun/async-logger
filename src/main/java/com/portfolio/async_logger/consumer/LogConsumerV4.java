package com.portfolio.async_logger.consumer;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogConsumerV4 {

    private final BlockingQueue<String> logQueue;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE=1000;

    @PostConstruct
    public void startConsuming(){
        Thread consumerThread= new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                try{
                    List<String> batchList = new ArrayList<>(BATCH_SIZE);

                    batchList.add(logQueue.take());
                    logQueue.drainTo(batchList, BATCH_SIZE - 1);


                    saveLogsInBatch(batchList);

                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        });
        consumerThread.setName("LogConsumer-Thread-V4");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    public void saveLogsInBatch(List<String> logStrings){

        long startTime = System.nanoTime();
        String sql = "INSERT INTO log(ip_address, visited_url, created_at) VALUES(?,?,?)";

        List<Object[]> batchArgs= logStrings.stream()
                        .map(logString->{
                            String[] parts = logString.split(",", 3);
                            return new Object[]{
                                    parts[0],
                                    parts[1],
                                    Timestamp.valueOf(parts[2])
                            };
                        }).toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);

        long endTime = System.nanoTime();
        long durationMS= (endTime-startTime)/1_000_000;

        log.info("[V4 BATCH] {}개의 로그 처리완료. 총시간 :{}ms", logStrings.size(), durationMS);
    }



}
