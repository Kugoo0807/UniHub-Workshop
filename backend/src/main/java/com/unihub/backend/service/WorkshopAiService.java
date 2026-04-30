package com.unihub.backend.service;

import com.unihub.backend.dto.ai.AiSummaryResponse;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopAiService {

    private final WorkshopRepository workshopRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Async
    @Transactional
    public void generateSummaryAsync(Long workshopId, byte[] fileBytes, String filename) {
        try {
            log.info("Starting AI summary generation for workshop {}", workshopId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("workshop_id", workshopId);
            
            // Convert byte[] to Resource for RestTemplate
            ByteArrayResource fileAsResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "document.pdf";
                }
            };
            body.add("file", fileAsResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiSummaryResponse> response = restTemplate.postForEntity(
                    aiServiceUrl + "/summarize", requestEntity, AiSummaryResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String summary = response.getBody().getSummary();
                workshopRepository.updateDescription(workshopId, summary);
                log.info("Successfully updated description for workshop {}", workshopId);
            } else {
                log.warn("FastAPI returned non-200 status for workshop {}: {}", workshopId, response.getStatusCode());
            }

        } catch (Exception e) {
            log.warn("AI Summary failed for workshop {}: {}", workshopId, e.getMessage());
        }
    }
}
