package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.service.PaymentService;
import com.unihub.backend.service.RegistrationService;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.List;
@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final PaymentService paymentService;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final com.unihub.backend.repository.WorkshopRepository workshopRepository;

    @Qualifier("seatDecrScript")
    private final String seatDecrScript;

    @Qualifier("seatReserveScript")
    private final String seatReserveScript;


    @PostMapping("/free")
    public ResponseEntity<?> registerFree(@RequestBody RegistrationRequest req, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        RegistrationResponse resp = registrationService.registerFree(req, user, seatDecrScript);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/paid")
    public ResponseEntity<?> initiatePaid(@RequestBody RegistrationRequest req, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        RegistrationResponse resp = registrationService.initiatePaid(req, user, seatReserveScript);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/payments")
    public ResponseEntity<?> processPayment(@RequestBody PaymentRequest req) {
        Payment payment = paymentService.processPayment(req, seatReserveScript);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/me/{workshopId}")
    public ResponseEntity<?> getMyRegistration(@PathVariable Long workshopId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        var user = userRepository.findById(userId).orElseThrow();
        var workshop = workshopRepository.findById(workshopId).orElseThrow();
        Optional<Registration> reg = registrationRepository.findByUserAndWorkshop(user, workshop);
        // To keep it simple, return 404 if not found
        if (reg.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(reg.get());
    }
    @GetMapping("/my-registrations")
    public ResponseEntity<List<Registration>> getAllMyRegistrations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();

        List<Registration> registrations = registrationRepository.findByUser(user);

        return ResponseEntity.ok(registrations);
    }
}
