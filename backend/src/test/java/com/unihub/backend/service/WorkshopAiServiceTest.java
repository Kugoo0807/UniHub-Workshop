package com.unihub.backend.service;

import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.dto.ai.AiSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopAiServiceTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WorkshopAiService workshopAiService;

    private MockMultipartFile mockFile;
    private final Long workshopId = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workshopAiService, "aiServiceUrl", "http://ai-service:8000");
        mockFile = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "dummy pdf content".getBytes());
    }

    @Test
    void generateSummaryAsync_Success_ShouldUpdateDescription() {
        // AI-UT-01 & AI-UT-05
        String expectedSummary = "This is a mock summary from AI.";
        AiSummaryResponse mockResponse = new AiSummaryResponse();
        mockResponse.setWorkshop_id(workshopId);
        mockResponse.setSummary(expectedSummary);

        ResponseEntity<AiSummaryResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class)))
                .thenReturn(responseEntity);

        // Act
        workshopAiService.generateSummaryAsync(workshopId, "dummy content".getBytes(), "test.pdf");

        // Assert
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class));
        verify(workshopRepository, times(1)).updateDescription(workshopId, expectedSummary);
    }

    @Test
    void generateSummaryAsync_Timeout_ShouldNotThrowExceptionAndNotUpdate() {
        // AI-UT-02
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class)))
                .thenThrow(new RestClientException("Read timed out"));

        // Act
        workshopAiService.generateSummaryAsync(workshopId, "dummy content".getBytes(), "test.pdf");

        // Assert
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class));
        verify(workshopRepository, never()).updateDescription(anyLong(), anyString());
    }

    @Test
    void generateSummaryAsync_ApiReturns500_ShouldNotThrowExceptionAndNotUpdate() {
        // AI-UT-03
        ResponseEntity<AiSummaryResponse> responseEntity = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class)))
                .thenReturn(responseEntity);

        // Act
        workshopAiService.generateSummaryAsync(workshopId, "dummy content".getBytes(), "test.pdf");

        // Assert
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class));
        verify(workshopRepository, never()).updateDescription(anyLong(), anyString());
    }

    @Test
    void generateSummaryAsync_ConnectionRefused_ShouldNotThrowExceptionAndNotUpdate() {
        // AI-UT-04
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Act
        workshopAiService.generateSummaryAsync(workshopId, "dummy content".getBytes(), "test.pdf");

        // Assert
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(AiSummaryResponse.class));
        verify(workshopRepository, never()).updateDescription(anyLong(), anyString());
    }
}
