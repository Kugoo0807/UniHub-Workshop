package com.unihub.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "System health and diagnostics endpoints.")
public class SystemController {

    @GetMapping("/test")
    @Operation(summary = "Check server availability", description = "Returns a simple message to verify the API is up.")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Server is running");
    }
}

