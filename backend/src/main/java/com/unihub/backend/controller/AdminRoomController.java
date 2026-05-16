package com.unihub.backend.controller;

import com.unihub.backend.config.OpenApiConfig;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.dto.RoomRequest;
import com.unihub.backend.dto.RoomResponse;
import com.unihub.backend.service.RoomService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
@Tag(name = "Room Management", description = "CRUD endpoints for rooms (Admin only).")
public class AdminRoomController {

    private final RoomService roomService;

    // ─── GET all ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all rooms", description = "Returns all rooms ordered by ID. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room list retrieved")
    })
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    // ─── GET one ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get room details", description = "Returns a single room by ID. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room found"),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    // ─── POST create ──────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new room", description = "Creates a new room. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Room created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Room name already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    // ─── PUT update ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update room name/capacity",
            description = "Updates name and capacity of a room. Does NOT update the map image. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room updated"),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Room name already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    // ─── POST map image ───────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/map-image", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload floor map image",
            description = "Uploads an image file to Cloudinary and stores the URL. " +
                    "Accepted types: jpeg, png, webp. Max size: 5MB. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Map image uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file (empty or wrong type)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File size exceeds 5MB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RoomResponse> uploadMapImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(roomService.uploadMapImage(id, file));
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a room",
            description = "Deletes a room that has no DRAFT or PUBLISHED workshops. Requires ADMIN role.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room deleted"),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Room is used by active workshops",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(Map.of("message", "Room deleted successfully"));
    }
}
