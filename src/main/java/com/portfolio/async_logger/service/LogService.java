package com.portfolio.async_logger.service;

import com.portfolio.async_logger.domain.Log;
import com.portfolio.async_logger.domain.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LogService {

    @Autowired
    private LogRepository logRepository;

    @Transactional
    public void saveLog(String ip, String url){
        try{
            Log newLog = new Log(ip, url, LocalDateTime.now());
            logRepository.save(newLog);

        }catch (Exception e){

        }
    }


}
