package com.unihub.backend.controller;

import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.security.JwtAuthFilter;
import com.unihub.backend.security.JwtUtil;
import com.unihub.backend.service.PaymentService;
import com.unihub.backend.service.RegistrationService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerUnauthorizedIT {

    private MockMvc mockMvc;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        RegistrationController controller = new RegistrationController(
                registrationService,
                paymentService,
                registrationRepository,
                userRepository,
                workshopRepository,
                "seatDecrScript",
                "seatReserveScript"
        );

        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtil);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(jwtAuthFilter)
                .build();
    }

    @Test
    void payments_shouldReturn401_whenJwtMalformed() throws Exception {
        when(jwtUtil.validateAccessToken("invalid-token"))
                .thenThrow(new MalformedJwtException("bad token"));

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType("application/json")
                        .content("""
                                {"registrationId":1,"amount":100000,"idempotencyKey":"idem-401"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid token")));
    }
}
