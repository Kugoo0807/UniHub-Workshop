package com.unihub.backend.service;

import com.unihub.backend.config.StudentSyncProperties;
import com.unihub.backend.dto.StudentSyncResponse;
import com.unihub.backend.event.CsvSyncCompletedEvent;
import com.unihub.backend.exception.FileStorageException;
import com.unihub.backend.service.csv.CsvFetchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentSyncService {

    private static final String UPSERT_SQL = """
        INSERT INTO users (student_code, full_name, email, phone_number, password, role, status)
        VALUES (?, ?, ?, ?, NULL, 'STUDENT', 'INACTIVE')
        ON CONFLICT (student_code) DO UPDATE SET
            full_name = EXCLUDED.full_name,
            email = EXCLUDED.email,
            phone_number = EXCLUDED.phone_number,
            role = 'STUDENT'
       """;

    private static final int MAX_RETRY = 3;

    private final List<CsvFetchStrategy> fetchStrategies;
    private final StudentSyncProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    public StudentSyncResponse syncStudents(boolean manualTrigger) {
        Instant startTime = Instant.now();
        CsvFetchStrategy strategy = resolveStrategy();
        int batchSize = Math.max(100, properties.getBatchSize());

        long success = 0;
        long failed = 0;

        try (InputStream input = strategy.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

            // Read and parse the header line to map column names to indices
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV header is missing");
            }

            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> headerMap = mapHeaders(headers);

            // Validate required columns and get their indices
            int studentCodeIndex = getRequiredIndex(headerMap, "student_code");
            int fullNameIndex = getRequiredIndex(headerMap, "full_name");
            int emailIndex = getRequiredIndex(headerMap, "email");
            Integer phoneIndex = headerMap.get("phone_number");

            // Process CSV lines in batches
            List<StudentCsvRow> batch = new ArrayList<>(batchSize);
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                try {
                    // Parse the CSV line into values and extract required fields
                    List<String> values = parseCsvLine(line);
                    String studentCode = getValue(values, studentCodeIndex);
                    String fullName = getValue(values, fullNameIndex);
                    String email = getValue(values, emailIndex);
                    String phone = phoneIndex != null ? getValue(values, phoneIndex) : null;
                    if (phone != null && phone.isBlank()) {
                        phone = null;
                    }

                    if (studentCode.isBlank() || fullName.isBlank() || email.isBlank()) {
                        throw new IllegalArgumentException("Missing required columns");
                    }

                    // Add the parsed student data to the batch
                    batch.add(new StudentCsvRow(studentCode, fullName, email, phone));

                    // If the batch size is reached, execute the batch and clear it for the next set of records
                    if (batch.size() >= batchSize) {
                        success += executeBatchWithRetry(batch);
                        batch.clear();
                    }
                } catch (Exception ex) {
                    failed++;
                    log.warn("SYNC_ROW_PARSE_ERROR line {}: {}", lineNumber, ex.getMessage());
                }
            }

            // Process any remaining records in the batch
            if (!batch.isEmpty()) {
                success += executeBatchWithRetry(batch);
            }

        } catch (IOException ex) {
            throw new FileStorageException("Failed to read CSV stream");
        }

        // Log the summary of the sync operation
        long processed = success + failed;
        log.info("Student CSV sync finished. total={}, success={}, failed={}", processed, success, failed);

        long durationSeconds = Duration.between(startTime, Instant.now()).toSeconds();
        eventPublisher.publishEvent(new CsvSyncCompletedEvent(processed, success, failed, durationSeconds));

        // Build and return the response object with sync results and metadata
        return StudentSyncResponse.builder()
                .totalRows(processed)
                .successRows(success)
                .failedRows(failed)
                .batchSize(batchSize)
                .strategy(strategy.getStrategyName())
                .manualTrigger(manualTrigger)
                .build();
    }

    // --- Helper methods ---

    /**
     * Resolves the CSV fetch strategy based on configuration.
     * Throws IllegalArgumentException if the configured strategy is not found.
     */
    private CsvFetchStrategy resolveStrategy() {
        String configured = properties.getFetchStrategy();
        return fetchStrategies.stream()
                .filter(strategy -> strategy.getStrategyName().equalsIgnoreCase(configured))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported CSV fetch strategy: " + configured));
    }


    /**
     * Executes a batch of student records with retry logic for transient database errors.
     * Retries up to MAX_RETRY times before throwing an exception.
     *
     * @param batch List of StudentCsvRow to be upserted into the database
     * @return number of records successfully processed in the batch
     */
    private long executeBatchWithRetry(List<StudentCsvRow> batch) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.executeWithoutResult(status -> jdbcTemplate.batchUpdate(UPSERT_SQL, batch, batch.size(),
                        (ps, row) -> {
                            ps.setString(1, row.studentCode());
                            ps.setString(2, row.fullName());
                            ps.setString(3, row.email());
                            ps.setString(4, row.phoneNumber());
                        }));
                return batch.size();
            } catch (DataAccessException ex) {
                log.error("SYNC_DB_TIMEOUT attempt {} failed: {}", attempt, ex.getMessage());
                if (attempt >= MAX_RETRY) {
                    throw new RuntimeException("SYNC_DB_TIMEOUT");
                }
            }
        }
    }

    /**
     * Maps normalized header names to their column indices.
     * Normalization includes trimming, lowercasing, and replacing spaces/hyphens with underscores.
     *
     * @param headers List of header names from the CSV
     * @return Map of normalized header names to their indices
     */
    private Map<String, Integer> mapHeaders(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = normalizeHeader(headers.get(i));
            if (!key.isBlank()) {
                map.put(key, i);
            }
        }
        return map;
    }

    /**
     * Retrieves the index of a required column from the header map.
     * Throws IllegalArgumentException if the column is missing.
     *
     * @param headerMap Map of normalized header names to their indices
     * @param name      The normalized name of the required column
     * @return The index of the required column in the CSV
     */
    private int getRequiredIndex(Map<String, Integer> headerMap, String name) {
        Integer index = headerMap.get(name);
        if (index == null) {
            throw new IllegalArgumentException("CSV header missing required column: " + name);
        }
        return index;
    }

    /**
     * Normalizes a header name by trimming, lowercasing, and replacing spaces/hyphens with underscores.
     * Also handles common variations of required column names.
     *
     * @param header The original header name from the CSV
     * @return The normalized header name used for mapping
     */
    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String normalized = header.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("\uFEFF", "");
        normalized = normalized.replace(" ", "_");
        normalized = normalized.replace("-", "_");

        if (normalized.equals("studentcode")) {
            return "student_code";
        }
        if (normalized.equals("fullname")) {
            return "full_name";
        }
        if (normalized.equals("phonenumber")) {
            return "phone_number";
        }

        return normalized;
    }

    /**
     * Parses a CSV line into a list of values, handling quoted fields and escaped quotes.
     *
     * @param line The CSV line to parse
     * @return List of values extracted from the CSV line
     */
    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    /**
     * Retrieves the value at the specified index from the list of values.
     * Trims the value and throws IllegalArgumentException if the index is out of bounds.
     *
     * @param values List of values from a CSV line
     * @param index  The index of the value to retrieve
     * @return The trimmed value at the specified index
     */
    private String getValue(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            throw new IllegalArgumentException("Invalid column index");
        }
        return values.get(index).trim();
    }

    /**
     * Record class representing a row of student data extracted from the CSV.
     * Contains fields for student code, full name, email, and phone number.
     */
    private record StudentCsvRow(String studentCode, String fullName, String email, String phoneNumber) {
    }
}
