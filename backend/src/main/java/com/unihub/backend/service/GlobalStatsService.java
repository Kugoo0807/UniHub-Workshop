package com.unihub.backend.service;

import com.unihub.backend.dto.GlobalStatsResponse;
import com.unihub.backend.dto.GlobalStatsResponse.*;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalStatsService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;

    public GlobalStatsResponse getGlobalStats() {
        // ── 1. Revenue ────────────────────────────────────────────────
        Long totalRevenue = paymentRepository.sumRevenue();

        // ── 2. Workshop count ─────────────────────────────────────────
        long totalWorkshops = workshopRepository.count();

        // ── 3. Total SUCCESS registrations ────────────────────────────
        long totalRegistrations = registrationRepository.countByStatus("SUCCESS");

        // ── 4. Payment success/failure rates (only paid workshops) ────
        long totalAttempts = paymentRepository.countTotalAttemptsForPaidWorkshops();
        long completedPaid = paymentRepository.countCompletedForPaidWorkshops();
        long failedPaid    = paymentRepository.countFailedForPaidWorkshops();

        double paymentSuccessRate = totalAttempts > 0
                ? round((double) completedPaid / totalAttempts * 100) : 0.0;
        double paymentFailureRate = totalAttempts > 0
                ? round((double) failedPaid / totalAttempts * 100) : 0.0;

        // ── 5. Actual participation rate (COMPLETED workshops only) ──
        // Scoped to past events so the metric is meaningful
        long totalInCompleted     = registrationRepository.countByWorkshopCompleted();
        long successInCompleted   = registrationRepository.countByStatusAndWorkshopCompleted("SUCCESS");
        long cancelledInCompleted = registrationRepository.countByStatusAndWorkshopCompleted("CANCELLED");

        double actualParticipationRate = totalInCompleted > 0
                ? round((double) successInCompleted / totalInCompleted * 100) : 0.0;

        // ── 6. Cancellation rate (COMPLETED workshops only) ───────────
        double cancellationRate = totalInCompleted > 0
                ? round((double) cancelledInCompleted / totalInCompleted * 100) : 0.0;

        // ── 7. Speakers ───────────────────────────────────────────────
        List<SpeakerStat> topSpeakers = new ArrayList<>();
        List<Object[]> speakerRows = registrationRepository.countWorkshopsBySpeaker();
        for (Object[] row : speakerRows) {
            topSpeakers.add(SpeakerStat.builder()
                    .speaker(String.valueOf(row[0]))
                    .workshopCount(((Number) row[1]).longValue())
                    .build());
        }

        // ── 8. Room utilization ───────────────────────────────────────
        List<RoomUtilization> roomUtilization = new ArrayList<>();
        List<Object[]> roomRows = registrationRepository.roomUtilizationStats();
        for (Object[] row : roomRows) {
            roomUtilization.add(RoomUtilization.builder()
                    .roomName(String.valueOf(row[0]))
                    .capacity(((Number) row[1]).intValue())
                    .workshopCount(((Number) row[2]).longValue())
                    .avgFillRate(row[3] != null ? round(((Number) row[3]).doubleValue()) : 0.0)
                    .build());
        }

        // ── 9. Registrations by hour ──────────────────────────────────
        List<HourlyRegistration> registrationsByHour = new ArrayList<>();
        List<Object[]> hourRows = registrationRepository.countByHourOfDay();
        for (Object[] row : hourRows) {
            registrationsByHour.add(HourlyRegistration.builder()
                    .hour(((Number) row[0]).intValue())
                    .count(((Number) row[1]).longValue())
                    .build());
        }

        // ── 10. Workshop fill rates ───────────────────────────────────
        List<WorkshopFillRate> workshopFillRates = new ArrayList<>();
        List<Object[]> fillRows = registrationRepository.workshopFillRates();
        for (Object[] row : fillRows) {
            int totalSlots = ((Number) row[2]).intValue();
            long registered = ((Number) row[3]).longValue();
            double fillRate = totalSlots > 0
                    ? round((double) registered / totalSlots * 100) : 0.0;
            workshopFillRates.add(WorkshopFillRate.builder()
                    .workshopId(((Number) row[0]).longValue())
                    .title(String.valueOf(row[1]))
                    .totalSlots(totalSlots)
                    .registered(registered)
                    .fillRate(fillRate)
                    .build());
        }

        return GlobalStatsResponse.builder()
                .totalRevenue(totalRevenue)
                .totalWorkshops(totalWorkshops)
                .totalRegistrations(totalRegistrations)
                .paymentSuccessRate(paymentSuccessRate)
                .paymentFailureRate(paymentFailureRate)
                .actualParticipationRate(actualParticipationRate)
                .cancellationRate(cancellationRate)
                .topSpeakers(topSpeakers)
                .roomUtilization(roomUtilization)
                .registrationsByHour(registrationsByHour)
                .workshopFillRates(workshopFillRates)
                .build();
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
