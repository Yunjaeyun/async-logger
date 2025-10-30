package com.portfolio.async_logger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Getter
@Slf4j
@Entity
@NoArgsConstructor
public class Log {

    @Id
    @GeneratedValue
    private Long id;

    private String ipAddress;
    private String visitedUrl;
    private LocalDateTime createdAt;

    public Log(String ipAddress, String visitedUrl, LocalDateTime createdAt) {
        this.ipAddress = ipAddress;
        this.visitedUrl = visitedUrl;
        this.createdAt = createdAt;
    }
}
