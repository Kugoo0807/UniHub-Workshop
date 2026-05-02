package com.unihub.backend.service;

import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.WorkshopRequest;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require}",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.datasource.username=${DB_USERNAME:postgres.pmoqxtxonplkxqtfyhnv}",
        "spring.datasource.password=${DB_PASSWORD:namtayto12312}",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public class RegistrationLoadTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkshopService workshopService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("seatDecrScript")
    private String seatDecrScript;

    private List<WorkshopResponse> testWorkshops = new ArrayList<>();
    private List<User> testUsers;

    private static final int TOTAL_REQUESTS = 12000;
    private static final int TOTAL_SLOTS_PER_WORKSHOP = 100;
    private static final int NUM_WORKSHOPS = 8;

    @BeforeAll
    void setup() {
        log.info("Setting up Load Test Data...");
        cleanup(); // Clean up any stale test data

        // 1. Create many synthetic workshops through the service to diversify the workload
        for (int w = 1; w <= NUM_WORKSHOPS; w++) {
            WorkshopRequest wsReq = new WorkshopRequest(
                    "LoadTest Workshop " + w,
                    "Load Test Seat Contention",
                    TOTAL_SLOTS_PER_WORKSHOP,
                    java.math.BigDecimal.ZERO,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2));
            testWorkshops.add(workshopService.createWorkshop(wsReq));
        }

        // 2. Create 12,000 synthetic students (in small batches for speed)
        List<User> usersToSave = new ArrayList<>();
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            usersToSave.add(User.builder()
                    .email("loadtest_" + i + "@unihub.com")
                    .fullName("Load Test User " + i)
                    .role("STUDENT")
                    .status("ACTIVE")
                    .studentCode("LD" + i)
                    .build());
        }
        log.info("Saving {} mock users to PostgreSQL database...", TOTAL_REQUESTS);
        testUsers = userRepository.saveAll(usersToSave);
        log.info("Finished setting up Load Test Data.");
    }

    @AfterAll
    void teardown() {
        log.info("Cleaning up Load Test Data...");
        cleanup();
        log.info("Finished cleaning up Load Test Data.");
    }

    private void cleanup() {
        // Use JdbcTemplate to remove data quickly and keep repository code clean
        try {
            jdbcTemplate.execute(
                    "DELETE FROM payments WHERE registration_id IN (SELECT id FROM registrations WHERE workshop_id IN (SELECT id FROM workshops WHERE title LIKE 'LoadTest Workshop%'))");
            jdbcTemplate.execute(
                    "DELETE FROM checkin_records WHERE registration_id IN (SELECT id FROM registrations WHERE workshop_id IN (SELECT id FROM workshops WHERE title LIKE 'LoadTest Workshop%'))");
            jdbcTemplate.execute(
                    "DELETE FROM registrations WHERE workshop_id IN (SELECT id FROM workshops WHERE title LIKE 'LoadTest Workshop%')");
            jdbcTemplate.execute("DELETE FROM workshops WHERE title LIKE 'LoadTest Workshop%'");
            jdbcTemplate.execute("DELETE FROM users WHERE email LIKE 'loadtest_%'");

            if (testWorkshops != null && !testWorkshops.isEmpty()) {
                for (WorkshopResponse w : testWorkshops) {
                    redisTemplate.delete("workshop:" + w.getId() + ":slots");
                    redisTemplate.delete("workshop:" + w.getId() + ":reservation:*"); // clear any stuck reservations
                }
            }
        } catch (Exception e) {
            log.warn("Cleanup failed partially, might be nothing to clean.", e);
        }
    }

    @Test
    void testZeroOverbookingWith12000Requests() throws InterruptedException {
        // Simulate 12,000 requests over 10 minutes (600,000 ms)
        // Phase 1: 60% of requests (7,200) in the first 3 minutes (180,000 ms) => 40 req/s
        // sleep 25ms
        // Phase 2: 40% of requests (4,800) over the next 7 minutes (420,000 ms) => ~11.4 req/s
        // => sleep 87ms

        ExecutorService executor = Executors.newFixedThreadPool(150);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>(TOTAL_REQUESTS));

        int phase1Requests = (int) (TOTAL_REQUESTS * 0.6); // 7200
        int sleepPhase1 = 25;

        int phase2Requests = TOTAL_REQUESTS - phase1Requests; // 4800
        int sleepPhase2 = 87;

        log.info("Starting Load Test... Phase 1 (3 mins): {} requests at 40 req/s", phase1Requests);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            User user = testUsers.get(i);
            RegistrationRequest req = new RegistrationRequest();
            // Spread the load evenly across different workshops (e.g., i % 5)
            WorkshopResponse targetWorkshop = testWorkshops.get(i % NUM_WORKSHOPS);
            req.setWorkshopId(targetWorkshop.getId());

            executor.submit(() -> {
                long reqStart = System.currentTimeMillis();
                try {
                    registrationService.registerFree(req, user, seatDecrScript);
                    successCount.incrementAndGet();
                } catch (com.unihub.backend.exception.InsufficientSeatsException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Unexpected error", e);
                } finally {
                    long durationReq = System.currentTimeMillis() - reqStart;
                    responseTimes.add(durationReq);
                    latch.countDown();
                }
            });

            // Simulate request arrival time distribution
            if (i < phase1Requests) {
                Thread.sleep(sleepPhase1);
            } else {
                if (i == phase1Requests) {
                    log.info("Entering Phase 2 (7 mins): {} requests at 11.4 req/s", phase2Requests);
                }
                Thread.sleep(sleepPhase2);
            }
        }

        // Wait for the worker threads to finish processing after dispatch completes
        log.info("All requests dispatched. Waiting for pending DB transactions to complete...");
        latch.await(2, TimeUnit.MINUTES);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Load Test completed in {} ms (expected ~ 10 minutes)", duration);
        log.info("Successful Registrations: {}", successCount.get());
        log.info("Failed (Insufficient Seats): {}", failCount.get());
        log.info("Errors (Other exceptions): {}", errorCount.get());

        // Assert the atomic seat-protection logic
        int expectedTotalSuccess = TOTAL_SLOTS_PER_WORKSHOP * NUM_WORKSHOPS;
        Assertions.assertEquals(expectedTotalSuccess, successCount.get(),
                "Only " + expectedTotalSuccess + " registrations are allowed to succeed");
        Assertions.assertEquals(TOTAL_REQUESTS - expectedTotalSuccess, failCount.get(),
                "The remaining requests must report sold out");
        Assertions.assertEquals(0, errorCount.get(),
                "No other exceptions are allowed (for example, deadlocks or DB timeouts)");

        // Compute benchmark under 200ms
        long totalTime = responseTimes.stream().mapToLong(Long::longValue).sum();
        double avgTime = totalTime / (double) responseTimes.size();
        long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        List<Long> sortedTimes = new ArrayList<>(responseTimes);
        Collections.sort(sortedTimes);
        long p95Time = sortedTimes.get((int) (sortedTimes.size() * 0.95));

        log.info("=== PERFORMANCE BENCHMARK ===");
        log.info("Total Requests: {}", responseTimes.size());
        log.info("Average Response Time: {} ms", String.format("%.2f", avgTime));
        log.info("95th Percentile (P95) Time: {} ms", p95Time);
        log.info("Max Response Time: {} ms", maxTime);
        log.info("===============================");

        // Evaluate the criterion: the registration API should respond in under 200ms under normal conditions
        Assertions.assertTrue(avgTime < 200, "Average response time exceeded 200ms: " + avgTime + "ms");
        // This can be tightened further by requiring p95 < 200ms depending on the agreed spec
        // Assertions.assertTrue(p95Time < 200, "95% of requests must respond in under 200ms");
    }
}
