package com.portfolio.async_logger.consumer;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.domain.LogRepository;
import com.portfolio.async_logger.dto.LogDataDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

/***
 * V2의 워커스레드 (주방 요리사역할)
 */

@Component
@RequiredArgsConstructor
public class LogConsumer {

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

                    logRepository.save(logEntity);




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
