package com.portfolio.async_logger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class AppConfig {

    @Bean
    public BlockingQueue<LogDataDto> logQueue(){
        return new LinkedBlockingQueue<>(100000);
    }

}
