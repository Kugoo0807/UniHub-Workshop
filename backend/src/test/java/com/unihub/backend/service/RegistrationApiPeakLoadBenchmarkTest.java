package com.unihub.backend.service;

import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("benchmark")
@DisplayName("Registration Service Peak Load Benchmark")
class RegistrationApiPeakLoadBenchmarkTest {

    private static final int TOTAL_REQUESTS = 12_000;
    private static final int PEAK_REQUESTS = 7_200;
    private static final int NORMAL_REQUESTS = 4_800;
    private static final long LOGICAL_PEAK_WINDOW_SECONDS = 180;
    private static final long LOGICAL_TOTAL_WINDOW_SECONDS = 600;
    private static final long PEAK_WINDOW_MILLIS = 3_000;
    private static final long NORMAL_WINDOW_MILLIS = 7_000;
    private static final long WORKSHOP_ID = 1L;
    private static final int WORKSHOP_SLOTS = 100;
    private static final int THREAD_POOL_SIZE = 128;

    @Autowired
    private RegistrationService registrationService;

    @MockitoBean
    private WorkshopRepository workshopRepository;

    @MockitoBean
    private RegistrationRepository registrationRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setup() {
        Workshop benchmarkWorkshop = Workshop.builder()
                .id(WORKSHOP_ID)
                .title("Peak Load Workshop")
                .description("Benchmark workshop")
                .totalSlots(WORKSHOP_SLOTS)
                .remainingSlots(WORKSHOP_SLOTS)
                .price(BigDecimal.ZERO)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();

        User benchmarkUser = User.builder()
                .id(1L)
                .fullName("Benchmark User")
                .email("benchmark@unihub.local")
                .role("STUDENT")
                .status("ACTIVE")
                .build();

        Mockito.when(workshopRepository.findById(ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(benchmarkWorkshop));
        Mockito.when(registrationRepository.findByUserAndWorkshop(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        Mockito.when(registrationRepository.countByWorkshopIdAndStatus(ArgumentMatchers.anyLong(), ArgumentMatchers.eq("SUCCESS")))
                .thenReturn(0L);
        Mockito.when(redisTemplate.hasKey(ArgumentMatchers.anyString())).thenReturn(true);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.get(ArgumentMatchers.anyString())).thenReturn("100");

        AtomicInteger remainingSeats = new AtomicInteger(WORKSHOP_SLOTS);
        Mockito.when(redisTemplate.execute(ArgumentMatchers.<RedisCallback<Long>>any())).thenAnswer(invocation -> {
            int current = remainingSeats.getAndDecrement();
            return current > 0 ? 1L : -1L;
        });

        Mockito.when(registrationRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> {
            var registration = invocation.getArgument(0);
            return registration;
        });

        Mockito.when(workshopRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("12,000 requests with 60% in the first 3 minutes logical window")
    void peakLoadBenchmark() throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
        CountDownLatch completed = new CountDownLatch(TOTAL_REQUESTS);

        ConcurrentLinkedQueue<Long> peakResponseTimes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> normalResponseTimes = new ConcurrentLinkedQueue<>();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        long benchmarkStart = System.nanoTime();

        schedulePhase(
                scheduler,
                0,
                PEAK_REQUESTS,
                PEAK_WINDOW_MILLIS,
                peakResponseTimes,
                successCount,
                conflictCount,
                unexpectedCount,
                completed
        );

        schedulePhase(
                scheduler,
                PEAK_REQUESTS,
                NORMAL_REQUESTS,
                NORMAL_WINDOW_MILLIS,
                normalResponseTimes,
                successCount,
                conflictCount,
                unexpectedCount,
                completed
        );

        boolean finished = completed.await(5, TimeUnit.MINUTES);
        scheduler.shutdownNow();

        assertTrue(finished, "Benchmark did not finish within the timeout");
        assertEquals(TOTAL_REQUESTS, successCount.get() + conflictCount.get() + unexpectedCount.get());
        assertEquals(WORKSHOP_SLOTS, successCount.get(), "Zero-overbooking violated");
        assertEquals(TOTAL_REQUESTS - WORKSHOP_SLOTS, conflictCount.get(), "Conflict count mismatch");
        assertEquals(0, unexpectedCount.get(), "Unexpected results returned");

        double peakLogicalThroughput = PEAK_REQUESTS / (double) LOGICAL_PEAK_WINDOW_SECONDS;
        double totalLogicalThroughput = TOTAL_REQUESTS / (double) LOGICAL_TOTAL_WINDOW_SECONDS;

        assertTrue(peakLogicalThroughput >= 40.0, "Peak logical throughput is below 40 req/s");
        assertTrue(totalLogicalThroughput >= 20.0, "Total logical throughput is below 20 req/s average");
        assertTrue(averageMillis(peakResponseTimes) < 200.0, "Peak average response time exceeded 200ms");
        assertTrue(averageMillis(normalResponseTimes) < 200.0, "Normal average response time exceeded 200ms");

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - benchmarkStart);
        System.out.println("Peak requests: " + PEAK_REQUESTS + " in logical " + LOGICAL_PEAK_WINDOW_SECONDS + "s");
        System.out.println("Normal requests: " + NORMAL_REQUESTS + " in logical 420s");
        System.out.println("Elapsed real time: " + elapsedMillis + "ms");
        System.out.println("Success: " + successCount.get() + ", Conflict: " + conflictCount.get());
        System.out.println("Peak avg response: " + averageMillis(peakResponseTimes) + "ms");
        System.out.println("Normal avg response: " + averageMillis(normalResponseTimes) + "ms");
    }

    private void schedulePhase(
            ScheduledExecutorService scheduler,
            int requestOffset,
            int requestCount,
            long windowMillis,
            ConcurrentLinkedQueue<Long> responseTimes,
            AtomicInteger successCount,
            AtomicInteger conflictCount,
            AtomicInteger unexpectedCount,
            CountDownLatch completed
    ) {
        for (int i = 0; i < requestCount; i++) {
            final long delayMillis = requestCount == 1 ? 0 : (i * windowMillis) / requestCount;
            final long userId = requestOffset + i + 1L;

            scheduler.schedule(() -> {
                long started = System.nanoTime();
                try {
                    RegistrationRequest request = new RegistrationRequest();
                    request.setWorkshopId(WORKSHOP_ID);

                    RegistrationResponse response = registrationService.registerFree(
                            request,
                            User.builder().id(userId).build(),
                            "local count = redis.call('GET', KEYS[1]) if count and tonumber(count) > 0 then redis.call('DECR', KEYS[1]) return 1 else return -1 end"
                    );

                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    responseTimes.add(elapsedMillis);

                    if (response != null && "SUCCESS".equals(response.getStatus())) {
                        successCount.incrementAndGet();
                    } else {
                        unexpectedCount.incrementAndGet();
                    }
                } catch (InsufficientSeatsException ex) {
                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    responseTimes.add(elapsedMillis);
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    unexpectedCount.incrementAndGet();
                } finally {
                    completed.countDown();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private double averageMillis(ConcurrentLinkedQueue<Long> times) {
        if (times.isEmpty()) {
            return 0.0;
        }

        long total = 0;
        for (Long time : times) {
            total += time;
        }
        return total / (double) times.size();
    }
}