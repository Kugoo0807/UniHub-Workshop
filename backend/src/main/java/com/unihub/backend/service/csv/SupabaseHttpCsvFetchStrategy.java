package com.unihub.backend.service.csv;

import com.unihub.backend.config.StudentSyncProperties;
import com.unihub.backend.exception.FileStorageException;
import com.unihub.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.io.FilterInputStream;
import java.net.URL;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupabaseHttpCsvFetchStrategy implements CsvFetchStrategy {

    private final StudentSyncProperties properties;

    @Override
    public String getStrategyName() {
        return "SUPABASE_HTTP";
    }

    @Override
    public InputStream openStream() throws IOException {
        String sourceUrl = properties.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("CSV source URL is not configured");
        }

        URL url = new URL(sourceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(properties.getConnectTimeoutMs());
        connection.setReadTimeout(properties.getReadTimeoutMs());
        connection.setRequestProperty("Accept", "text/csv");

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.disconnect();
            throw new ResourceNotFoundException("CSV source unreachable");
        }

        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new FileStorageException("Failed to fetch CSV. HTTP status: " + status);
        }

        log.info("CSV fetch success: {}", sourceUrl);
        return new FilterInputStream(connection.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    connection.disconnect();
                }
            }
        };
    }
}
