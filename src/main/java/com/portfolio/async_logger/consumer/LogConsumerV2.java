package com.portfolio.async_logger.consumer;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.domain.LogRepository;
import com.portfolio.async_logger.dto.LogDataDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;

/***
 * V2의 워커스레드 (주방 요리사역할)
 */
@Slf4j
//@Component
@RequiredArgsConstructor
public class LogConsumerV2 {

    private final LogRepository logRepository;
    private final BlockingQueue<LogDataDto> logQueue;

    @PostConstruct
    public void startConsuming(){
        Thread consumerThread=new Thread(()->{
            try{
                while(!Thread.currentThread().isInterrupted()){
                    LogDataDto logDto = logQueue.take();

                    Log logEntity=new Log(
                            logDto.getIpAdress(),
                            logDto.getVisitedUrl(),
                            logDto.getCreatedAt()
                    );

                    // ==  스톱워치 측정 시작 (1개당 걸리는 ms)==
                    long startTime= System.nanoTime();

                    logRepository.save(logEntity);

                    long endTime=System.nanoTime();
                    long durationMs = (endTime - startTime)/ 1000000;

                    log.info("Consumer save() 1 log took: {} ms", durationMs);
                    // == 끝 ==



                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // "주방 요리사"
        consumerThread.setName("LogConsumer-Thread");
        consumerThread.setDaemon(true);
        consumerThread.start();

    }
}
