package com.unihub.backend.event;

public record CsvSyncCompletedEvent(
        long totalRows,
        long successRows,
        long failedRows,
        long durationSeconds
) {
}
