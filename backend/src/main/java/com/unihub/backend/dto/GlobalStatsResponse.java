package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@Schema(description = "Global statistics across all workshops.")
public class GlobalStatsResponse {

    // ── Tổng quan ──────────────────────────────────────────────
    @Schema(description = "Total revenue from COMPLETED payments")
    private Long totalRevenue;

    @Schema(description = "Total number of workshops")
    private Long totalWorkshops;

    @Schema(description = "Total SUCCESS registrations across all workshops")
    private Long totalRegistrations;

    // ── Tỉ lệ ──────────────────────────────────────────────────
    @Schema(description = "Payment success rate (%) for paid workshops")
    private Double paymentSuccessRate;

    @Schema(description = "Payment failure rate (%) = FAILED / (COMPLETED+FAILED)")
    private Double paymentFailureRate;

    @Schema(description = "Actual participation rate (%) = checked-in / registered")
    private Double actualParticipationRate;

    @Schema(description = "Cancellation rate (%) = CANCELLED registrations / total")
    private Double cancellationRate;

    // ── Speakers ────────────────────────────────────────────────
    @Schema(description = "Top speakers by number of workshops they hosted")
    private List<SpeakerStat> topSpeakers;

    // ── Phòng ────────────────────────────────────────────────────
    @Schema(description = "Room utilization: avg fill-rate per room")
    private List<RoomUtilization> roomUtilization;

    // ── Khung giờ đông nhất ─────────────────────────────────────
    @Schema(description = "Registrations grouped by hour-of-day (0-23)")
    private List<HourlyRegistration> registrationsByHour;

    // ── Workshop-level fill rates ────────────────────────────────
    @Schema(description = "Fill rate per workshop (for bar/radar chart)")
    private List<WorkshopFillRate> workshopFillRates;

    // ── Inner value objects ──────────────────────────────────────
    @Getter
    @Builder
    public static class SpeakerStat {
        private String speaker;
        private Long workshopCount;
    }

    @Getter
    @Builder
    public static class RoomUtilization {
        private String roomName;
        private Integer capacity;
        private Long workshopCount;
        private Double avgFillRate;
    }

    @Getter
    @Builder
    public static class HourlyRegistration {
        private Integer hour;
        private Long count;
    }

    @Getter
    @Builder
    public static class WorkshopFillRate {
        private Long workshopId;
        private String title;
        private Integer totalSlots;
        private Long registered;
        private Double fillRate;
    }
}
