package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentResultResponse;
import com.unihub.backend.service.IdempotencyService;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.dto.UserRegistrationResponse;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.service.RegistrationService;
import com.unihub.backend.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
public class WorkshopController {

    private final RegistrationService registrationService;
    private final WorkshopService workshopService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<List<WorkshopResponse>> getAllWorkshops() {
        return ResponseEntity.ok(workshopService.getPublishedWorkshops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkshopResponse> getWorkshopById(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getPublishedWorkshopById(id));
    }

    @GetMapping("/my-workshops")
    public ResponseEntity<List<UserRegistrationResponse>> getMyWorkshops(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new com.unihub.backend.exception.UnauthorizedException("Authentication required");
        }

        Long userId = ((Number) authentication.getPrincipal()).longValue();
        return ResponseEntity.ok(registrationService.getUserRegistrations(userId));
    }

    @PostMapping("/{id}/register")
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new com.unihub.backend.exception.UnauthorizedException("Authentication required");
        }
        Long userId = ((Number) authentication.getPrincipal()).longValue();
        RegistrationResponse resp = registrationService.register(id, userId);
        return ResponseEntity.status(resp.isPaidFlow() ? 202 : 201).body(resp);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentResultResponse> pay(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            Authentication authentication) {

        log.info("Received /workshops/{}/pay request with Idempotency-Key={}", id, idempotencyKey);

        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new com.unihub.backend.exception.UnauthorizedException("Authentication required");
        }
        Long userId = ((Number) authentication.getPrincipal()).longValue();

        IdempotencyState state = idempotencyService.getState(idempotencyKey);
        if (state == IdempotencyState.IN_FLIGHT) {
            return ResponseEntity.status(202).build();
        }

        PaymentResultResponse res = registrationService.processPayment(id, userId, idempotencyKey);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/cancel-registration")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable Long id,
            Authentication authentication) {
        
        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new com.unihub.backend.exception.UnauthorizedException("Authentication required");
        }
        Long userId = ((Number) authentication.getPrincipal()).longValue();
        
        registrationService.cancelRegistration(id, userId);
        return ResponseEntity.noContent().build();
    }
}
