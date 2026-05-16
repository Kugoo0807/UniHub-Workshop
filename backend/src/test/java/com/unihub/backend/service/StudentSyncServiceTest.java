package com.unihub.backend.service;

import com.unihub.backend.config.StudentSyncProperties;
import com.unihub.backend.dto.StudentSyncResponse;
import com.unihub.backend.exception.FileStorageException;
import com.unihub.backend.service.csv.CsvFetchStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentSyncServiceTest {

    @Mock
    private CsvFetchStrategy csvFetchStrategy;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private StudentSyncProperties properties;

    private StudentSyncService studentSyncService;

    @BeforeEach
    void setUp() {
        properties = new StudentSyncProperties();
        properties.setFetchStrategy("SUPABASE_HTTP");
        properties.setBatchSize(1);

        studentSyncService = new StudentSyncService(
                List.of(csvFetchStrategy),
                properties,
                jdbcTemplate,
                transactionManager
        );

        when(csvFetchStrategy.getStrategyName()).thenReturn("SUPABASE_HTTP");
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any());
        doNothing().when(transactionManager).rollback(any());
    }

    @Test
    void syncStudents_success_batchesAndReturnsCounts() throws Exception {
        String csv = "student_code,full_name,email,phone_number\n"
                + "21127001,Nguyen Van A,21127001@student.hcmus.edu.vn,0900000001\n"
                + "21127002,Nguyen Van B,21127002@student.hcmus.edu.vn,\n";

        when(csvFetchStrategy.openStream()).thenReturn(stream(csv));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
                .thenReturn(new int[]{1}, new int[]{1});

        StudentSyncResponse response = studentSyncService.syncStudents(true);

        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getSuccessRows());
        assertEquals(0, response.getFailedRows());
        assertEquals(1, response.getBatchSize());
        assertEquals("SUPABASE_HTTP", response.getStrategy());
        assertTrue(response.isManualTrigger());

        verify(jdbcTemplate, times(2))
                .batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void syncStudents_rowParseError_skipsBadLines() throws Exception {
        String csv = "student_code,full_name,email,phone_number\n"
                + "21127001,Nguyen Van A,21127001@student.hcmus.edu.vn,0900000001\n"
                + "21127002,Nguyen Van B,,0900000002\n";

        when(csvFetchStrategy.openStream()).thenReturn(stream(csv));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});

        StudentSyncResponse response = studentSyncService.syncStudents(false);

        assertEquals(2, response.getTotalRows());
        assertEquals(1, response.getSuccessRows());
        assertEquals(1, response.getFailedRows());
        assertFalse(response.isManualTrigger());

        verify(jdbcTemplate, times(1))
                .batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void syncStudents_missingHeader_throwsException() throws Exception {
        when(csvFetchStrategy.openStream()).thenReturn(stream(""));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> studentSyncService.syncStudents(true));
        assertTrue(ex.getMessage().contains("CSV header"));
    }

        @Test
        void syncStudents_missingRequiredColumn_throwsException() throws Exception {
                String csv = "student_code,full_name,phone_number\n"
                                + "21127001,Nguyen Van A,0900000001\n";

                when(csvFetchStrategy.openStream()).thenReturn(stream(csv));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> studentSyncService.syncStudents(true));
                assertTrue(ex.getMessage().contains("missing required column"));
        }

    @Test
    void syncStudents_strategyNotFound_throwsException() {
        properties.setFetchStrategy("UNKNOWN");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> studentSyncService.syncStudents(true));
        assertTrue(ex.getMessage().contains("Unsupported CSV fetch strategy"));
    }

    @Test
    void syncStudents_openStreamIoError_throwsFileStorageException() throws Exception {
        when(csvFetchStrategy.openStream()).thenThrow(new IOException("boom"));

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> studentSyncService.syncStudents(true));
        assertTrue(ex.getMessage().contains("Failed to read CSV"));
    }

    @Test
    void syncStudents_dbRetryExceeded_throwsSyncDbTimeout() throws Exception {
        String csv = "student_code,full_name,email\n"
                + "21127001,Nguyen Van A,21127001@student.hcmus.edu.vn\n";

        when(csvFetchStrategy.openStream()).thenReturn(stream(csv));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
                .thenThrow(new DataAccessResourceFailureException("db timeout"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> studentSyncService.syncStudents(true));
        assertEquals("SYNC_DB_TIMEOUT", ex.getMessage());

        verify(jdbcTemplate, times(3))
                .batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
