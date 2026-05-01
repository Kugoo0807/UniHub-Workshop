package com.unihub.backend.service;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.gateway.PaymentGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
public class PaymentServiceContentionTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("seatReserveScript")
    private String seatReserveScript;

    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    private Long currentWorkshopId;

    @BeforeEach
    void setup() {
        Mockito.when(paymentGatewayClient.callGateway(any())).thenReturn("tx-dummy-123");

        paymentRepository.deleteAll();
        registrationRepository.deleteAll();
        workshopRepository.deleteAll();

        Workshop workshop = Workshop.builder()
                .title("Workshop Test Da Luong")
                .description("Test tranh chap ghe")
                .price(new BigDecimal("100000.00"))
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .totalSlots(10)
                .remainingSlots(10)
                .build();

        workshop = workshopRepository.saveAndFlush(workshop);
        currentWorkshopId = workshop.getId();

        String redisKey = "workshop:" + currentWorkshopId + ":slots";
        redisTemplate.delete(redisKey);
        redisTemplate.opsForValue().set(redisKey, "10");

        // Xoa sach Idempotency Key tren Redis de tranh doc nham ket qua "no_seat" tu lan test truoc
        for (int i = 0; i < 100; i++) {
            redisTemplate.delete("payment:idem:idem-concurrent-" + i);
        }
    }

    @Test
    public void testSeatContention_100Users_10Seats() throws InterruptedException {
        int numberOfThreads = 100;
        int availableSeats = 10;

        List<Long> registrationIds = new ArrayList<>();
        Workshop savedWorkshop = workshopRepository.findById(currentWorkshopId).orElseThrow();

        for (int i = 0; i < numberOfThreads; i++) {
            Registration reg = Registration.builder()
                    .workshop(savedWorkshop)
                    .status("PENDING")
                    .qrCode("dummy-qr-code-" + i)
                    .build();
            reg = registrationRepository.saveAndFlush(reg);
            registrationIds.add(reg.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyThreadCounter = new CountDownLatch(numberOfThreads);
        CountDownLatch startingGun = new CountDownLatch(1);
        CountDownLatch completedThreadCounter = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final Long regId = registrationIds.get(i);
            final int userId = i;

            executor.submit(() -> {
                try {
                    PaymentRequest req = new PaymentRequest();
                    req.setRegistrationId(regId);
                    req.setAmount(new BigDecimal("100000.00"));
                    req.setIdempotencyKey("idem-concurrent-" + userId);

                    readyThreadCounter.countDown();
                    startingGun.await();

                    paymentService.processPayment(req, seatReserveScript);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    completedThreadCounter.countDown();
                }
            });
        }

        readyThreadCounter.await();
        startingGun.countDown();
        completedThreadCounter.await();

        System.out.println("Thanh cong: " + successCount.get());
        System.out.println("That bai: " + failCount.get());

        assertEquals(availableSeats, successCount.get());
        assertEquals(numberOfThreads - availableSeats, failCount.get());

        String finalSlots = redisTemplate.opsForValue().get("workshop:" + currentWorkshopId + ":slots");
        assertEquals("0", finalSlots);
    }
}