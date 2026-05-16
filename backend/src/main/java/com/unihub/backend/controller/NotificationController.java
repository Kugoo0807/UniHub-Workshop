package com.unihub.backend.controller;

import com.unihub.backend.config.OpenApiConfig;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.dto.NotificationResponse;
import com.unihub.backend.dto.PageResponse;
import com.unihub.backend.exception.UnauthorizedException;
import com.unihub.backend.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for students and admins")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get my notifications", description = "Returns paginated notifications for the authenticated user.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof Number)) {
            throw new UnauthorizedException("Authentication required");
        }

        Long userId = ((Number) authentication.getPrincipal()).longValue();
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, page, size));
    }
}
