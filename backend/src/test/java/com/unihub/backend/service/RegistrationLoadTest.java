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
        "spring.datasource.hikari.maximum-pool-size=150",
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/postgres}",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.datasource.username=postgres.pmoqxtxonplkxqtfyhnv",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
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
        cleanup(); // Dọn dẹp dữ liệu cũ rác nếu có

        // 1. Tạo nhiều Workshop ảo thông qua Service để đa dạng hóa
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

        // 2. Tạo 12,000 Sinh viên ảo (thực hiện theo batch nhỏ để nhanh hơn)
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
        // Dùng JdbcTemplate để xoá dữ liệu nhanh và gọn, không làm bẩn code repository
        // chính
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
        // Mô phỏng 12,000 requests trong 10 phút (600,000 ms)
        // Giai đoạn 1: 60% requests (7200) trong 3 phút đầu (180,000 ms) => 40 req/s =>
        // sleep 25ms
        // Giai đoạn 2: 40% requests (4800) trong 7 phút sau (420,000 ms) => ~11.4 req/s
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
            // Phân bổ đều tải cho các workshop khác nhau (VD: i % 5)
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

            // Mô phỏng phân phối request arrival time
            if (i < phase1Requests) {
                Thread.sleep(sleepPhase1);
            } else {
                if (i == phase1Requests) {
                    log.info("Entering Phase 2 (7 mins): {} requests at 11.4 req/s", phase2Requests);
                }
                Thread.sleep(sleepPhase2);
            }
        }

        // Chờ các threads xử lý nốt phần còn lại sau khi đã dispatch xong
        log.info("All requests dispatched. Waiting for pending DB transactions to complete...");
        latch.await(2, TimeUnit.MINUTES);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Load Test completed in {} ms (expected ~ 10 minutes)", duration);
        log.info("Successful Registrations: {}", successCount.get());
        log.info("Failed (Insufficient Seats): {}", failCount.get());
        log.info("Errors (Other exceptions): {}", errorCount.get());

        // Kiểm tra (Assert) logic bảo vệ chỗ ngồi nguyên tử
        int expectedTotalSuccess = TOTAL_SLOTS_PER_WORKSHOP * NUM_WORKSHOPS;
        Assertions.assertEquals(expectedTotalSuccess, successCount.get(),
                "Chỉ được phép có đúng " + expectedTotalSuccess + " người đăng ký thành công");
        Assertions.assertEquals(TOTAL_REQUESTS - expectedTotalSuccess, failCount.get(),
                "Số còn lại phải báo lỗi hết chỗ");
        Assertions.assertEquals(0, errorCount.get(),
                "Không được có lỗi ngoại lệ nào khác (ví dụ: deadlock, timeout DB)");

        // Tính toán Benchmark < 200ms
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

        // Đánh giá tiêu chí: API đăng ký đáp ứng trong dưới 200ms trong điều kiện bình
        // thường
        Assertions.assertTrue(avgTime < 200, "Thời gian phản hồi trung bình vượt quá 200ms: " + avgTime + "ms");
        // Có thể chặt chẽ hơn bằng cách đòi hỏi p95 < 200ms tuỳ theo thoả thuận spec
        // Assertions.assertTrue(p95Time < 200, "95% request phải phản hồi dưới 200ms");
    }
}
