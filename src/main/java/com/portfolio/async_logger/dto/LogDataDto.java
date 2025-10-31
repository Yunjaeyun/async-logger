package com.portfolio.async_logger.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LogDataDto {
    private final String ipAdress;
    private final String visitedUrl;
    private final LocalDateTime createdAt;

    public LogDataDto(String ipAdress, String visitedUrl, LocalDateTime createdAt) {
        this.ipAdress = ipAdress;
        this.visitedUrl = visitedUrl;
        this.createdAt = createdAt;
    }

    public static LogDataDto of(String ipAddress, String visitedUrl) {
        return new LogDataDto(ipAddress, visitedUrl, LocalDateTime.now());
    }
}
