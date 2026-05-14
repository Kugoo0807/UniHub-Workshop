package com.unihub.backend.controller;

import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.dto.PageResponse;
import com.unihub.backend.dto.PaymentResultResponse;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.dto.UserRegistrationResponse;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.service.IdempotencyService;
import com.unihub.backend.service.RegistrationService;
import com.unihub.backend.service.WorkshopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
@Tag(name = "Workshops", description = "Workshop discovery and registration endpoints.")
public class WorkshopController {

    private final RegistrationService registrationService;
    private final WorkshopService workshopService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @Operation(summary = "List published workshops", description = "Returns paginated published workshops. If authenticated, user-specific registration info may be included.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop page retrieved")
    })
    public ResponseEntity<PageResponse<WorkshopResponse>> getAllWorkshops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ResponseEntity.ok(workshopService.getPublishedWorkshops(resolveUserId(authentication), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workshop details", description = "Returns a published workshop by ID.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop found"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopResponse> getWorkshopById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(workshopService.getPublishedWorkshopById(id, resolveUserId(authentication)));
    }

    @GetMapping("/my-workshops")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my workshops", description = "Returns registrations for the authenticated student.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registrations retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<UserRegistrationResponse>> getMyWorkshops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new com.unihub.backend.exception.UnauthorizedException("Authentication required");
        }

        Long userId = ((Number) authentication.getPrincipal()).longValue();
        return ResponseEntity.ok(registrationService.getUserRegistrations(userId, page, size));
    }

    @PostMapping("/{id}/register")
    @Operation(summary = "Register for a workshop", description = "Creates a workshop registration for the authenticated student.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registration created"),
        @ApiResponse(responseCode = "202", description = "Payment required or in progress"),
        @ApiResponse(responseCode = "400", description = "Invalid registration request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Workshop not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(summary = "Pay for a workshop", description = "Processes payment using an idempotency key.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment processed"),
        @ApiResponse(responseCode = "202", description = "Payment already in progress"),
        @ApiResponse(responseCode = "400", description = "Invalid payment request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Workshop not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(summary = "Cancel a registration", description = "Cancels the current user's registration for a workshop.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Registration cancelled"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Registration not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            return null;
        }
        return ((Number) authentication.getPrincipal()).longValue();
    }
}
