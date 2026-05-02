package com.unihub.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.exception.GlobalExceptionHandler;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.exception.PaymentServiceUnavailableException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.PaymentService;
import com.unihub.backend.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerStatusIT {

    private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

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

                mockMvc = MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

    @Test
    void payments_shouldReturn402_whenPaymentFailed() throws Exception {
        when(paymentService.processPayment(any(PaymentRequest.class), anyString()))
                .thenThrow(new PaymentFailedException("Card declined"));

        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(10L);
        req.setAmount(new BigDecimal("100000"));
        req.setIdempotencyKey("idem-402");

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status", is(402)))
                .andExpect(jsonPath("$.message", is("Card declined")));
    }

    @Test
    void payments_shouldReturn409_whenNoSeat() throws Exception {
        when(paymentService.processPayment(any(PaymentRequest.class), anyString()))
                .thenThrow(new InsufficientSeatsException("This workshop is sold out"));

        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(11L);
        req.setAmount(new BigDecimal("100000"));
        req.setIdempotencyKey("idem-409");

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("This workshop is sold out")));
    }

    @Test
    void payments_shouldReturn503_whenGatewayUnavailable() throws Exception {
        when(paymentService.processPayment(any(PaymentRequest.class), anyString()))
                .thenThrow(new PaymentServiceUnavailableException("The payment service is temporarily unavailable"));

        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(12L);
        req.setAmount(new BigDecimal("100000"));
        req.setIdempotencyKey("idem-503");

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", is("The payment service is temporarily unavailable")));
    }

    @Test
    void payments_retryWithSameIdempotencyKey_shouldReturnSameApiResult() throws Exception {
        Payment same = Payment.builder()
                .id(99L)
                .transactionId("tx-same")
                .idempotencyKey("idem-retry")
                .status("COMPLETED")
                .amount(new BigDecimal("100000"))
                .build();

        when(paymentService.processPayment(any(PaymentRequest.class), anyString()))
                .thenReturn(same);

        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(13L);
        req.setAmount(new BigDecimal("100000"));
        req.setIdempotencyKey("idem-retry");

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is("tx-same")))
                .andExpect(jsonPath("$.idempotencyKey", is("idem-retry")));

        mockMvc.perform(post("/api/v1/registrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is("tx-same")))
                .andExpect(jsonPath("$.idempotencyKey", is("idem-retry")));

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService, times(2)).processPayment(captor.capture(), anyString());
        assertThat(captor.getAllValues()).allMatch(v -> "idem-retry".equals(v.getIdempotencyKey()));
    }
}
