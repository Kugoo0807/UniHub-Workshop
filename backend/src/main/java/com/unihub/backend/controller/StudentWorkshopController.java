package com.unihub.backend.controller;

import com.unihub.backend.config.OpenApiConfig;
import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.entity.User;
import com.unihub.backend.repository.UserRepository;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/students/workshops")
@RequiredArgsConstructor
@Tag(name = "Student Workshop", description = "Endpoints for students to browse and register for workshops.")
public class StudentWorkshopController {

    private final WorkshopService workshopService;
    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    @Qualifier("seatDecrScript")
    private final String seatDecrScript;

    @Qualifier("seatReserveScript")
    private final String seatReserveScript;

    // ────────────── Public GET endpoints ──────────────

    @GetMapping
    @Operation(summary = "List all workshops (public)",
            description = "Returns all workshops. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop list retrieved")
    })
    public ResponseEntity<List<WorkshopResponse>> listWorkshops() {
        return ResponseEntity.ok(workshopService.getAllWorkshops());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workshop details (public)",
            description = "Returns a single workshop by ID. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workshop found"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkshopResponse> getWorkshop(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getWorkshopById(id));
    }

    // ────────────── STUDENT-only POST endpoints ──────────────

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Register for a free workshop",
            description = "Registers the authenticated student for a free workshop. Requires STUDENT role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict — no seats or already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationResponse> registerWorkshop(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();

        RegistrationRequest req = new RegistrationRequest();
        req.setWorkshopId(id);

        RegistrationResponse resp = registrationService.registerFree(req, user, seatDecrScript);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/{id}/register-paid")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Initiate registration for a paid workshop",
            description = "Creates a PENDING registration and reserves a seat for a paid workshop. Requires STUDENT role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pending registration created"),
            @ApiResponse(responseCode = "404", description = "Workshop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict — no seats or already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationResponse> registerPaidWorkshop(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();

        RegistrationRequest req = new RegistrationRequest();
        req.setWorkshopId(id);

        RegistrationResponse resp = registrationService.initiatePaid(req, user, seatReserveScript);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
