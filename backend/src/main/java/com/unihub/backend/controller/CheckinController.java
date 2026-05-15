package com.unihub.backend.controller;

import com.unihub.backend.dto.AttendeeResponse;
import com.unihub.backend.dto.CheckinSyncRequest;
import com.unihub.backend.dto.CheckinSyncResponse;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Check-in", description = "Endpoints for event check-in staff to manage and sync attendees.")
public class CheckinController {

    private final CheckinService checkinService;


    @PostMapping("/checkins/sync")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Sync check-in records", description = "Receives a batch of check-in records from the mobile app and performs idempotent inserts into the database.")
    @SecurityRequirement(name = com.unihub.backend.config.OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync completed, returns counts of successes, duplicates, and failures"),
            @ApiResponse(responseCode = "400", description = "Invalid request format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden (Requires STAFF role)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CheckinSyncResponse> syncCheckins(@Valid @RequestBody CheckinSyncRequest request) {
        return ResponseEntity.ok(checkinService.syncCheckins(request));
    }
}
