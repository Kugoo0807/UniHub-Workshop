package com.unihub.backend.controller;

import com.unihub.backend.config.OpenApiConfig;
import com.unihub.backend.dto.*;
import com.unihub.backend.service.WorkshopService;
import com.unihub.backend.service.WorkshopAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/workshops")
@RequiredArgsConstructor
@Tag(name = "Workshop Management", description = "CRUD endpoints for workshops.")
public class AdminWorkshopController {

    private final WorkshopService workshopService;
    private final WorkshopAiService workshopAiService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all workshops", description = "Returns all workshops. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop list retrieved")
    })
    public ResponseEntity<List<WorkshopResponse>> getAllWorkshops() {
        return ResponseEntity.ok(workshopService.getAllWorkshops());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get workshop details", description = "Returns a single workshop by ID. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop found"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopResponse> getWorkshopById(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getWorkshopById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new workshop", description = "Creates a new workshop. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workshop created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopResponse> createWorkshop(@Valid @RequestBody WorkshopRequest request) {
        WorkshopResponse created = workshopService.createWorkshop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a workshop", description = "Updates an existing workshop. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict — cannot change total_slots with existing registrations",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopResponse> updateWorkshop(
            @PathVariable Long id,
            @Valid @RequestBody WorkshopRequest request) {
        return ResponseEntity.ok(workshopService.updateWorkshop(id, request));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a workshop", description = "Sets a DRAFT workshop to PUBLISHED status. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop published"),
            @ApiResponse(responseCode = "400", description = "Only DRAFT can be published",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<java.util.Map<String, String>> publishWorkshop(@PathVariable Long id) {
        workshopService.publishWorkshop(id);
        return ResponseEntity.ok(java.util.Map.of("message", "Workshop has been published"));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancel a workshop", description = "Cancels a workshop by setting status to CANCELLED. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop cancelled"),
            @ApiResponse(responseCode = "400", description = "Cannot cancel (already completed/cancelled)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<java.util.Map<String, String>> cancelWorkshop(@PathVariable Long id) {
        workshopService.cancelWorkshop(id);
        return ResponseEntity.ok(java.util.Map.of("message", "Workshop has been cancelled"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a workshop", description = "Deletes a DRAFT workshop. Only DRAFT workshops can be deleted. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Workshop deleted"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict — only DRAFT workshops can be deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteWorkshop(@PathVariable Long id) {
        workshopService.deleteWorkshop(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get workshop statistics", description = "Returns registration statistics for a workshop. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopStatsResponse> getWorkshopStats(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getWorkshopStats(id));
    }

    @PostMapping(value = "/{id}/ai-summary", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload PDF for AI summary", description = "Generates a summary asynchronously. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Bad Request (e.g., not a PDF)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Payload Too Large (file > 10MB)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<java.util.Map<String, String>> uploadAiSummary(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {

        // 1. Check if workshop exists (Throws 404 if not found)
        workshopService.getWorkshopById(id);

        // 2. Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("File must be a PDF");
        }

        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(maxSize);
        }

        // 3. Read bytes on main thread (before HTTP Request completes and Tomcat deletes temp file)
        byte[] fileBytes;
        String originalFilename = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read file content");
        }

        // 4. Process asynchronously
        workshopAiService.generateSummaryAsync(id, fileBytes, originalFilename);

        // 5. Return 202 Accepted
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(java.util.Map.of("message", "AI summary is being processed, description will be updated soon."));
    }
}
