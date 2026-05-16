package com.unihub.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sync.students")
@Getter
@Setter
public class StudentSyncProperties {
    private String cron = "0 0 2 * * *";
    private int batchSize = 500;
    private String fetchStrategy = "SUPABASE_HTTP";
    private String sourceUrl;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;
}
