package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationTemplateService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");

    public NotificationContent buildRegistrationSuccess(Registration registration) {
        Workshop workshop = registration.getWorkshop();
        String studentName = registration.getUser().getFullName();
        String workshopTitle = workshop.getTitle();
        String startTime = formatDateTime(workshop.getStartTime());
        String roomName = workshop.getRoom() != null ? workshop.getRoom().getName() : "TBD";
        String qrCode = registration.getQrCode();
        String qrCodeUrl = buildQrCodeUrl(qrCode);

        String title = "Workshop registration confirmed";
        String body = """
            <p>Hi %s,</p>
            <p>Your registration is confirmed. Please keep this message for check-in.</p>
            <div style=\"margin:16px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f9fafb;\">
                <p><strong>Workshop:</strong> %s</p>
                <p><strong>Time:</strong> %s</p>
                <p><strong>Room:</strong> %s</p>
                <p><strong>Registration code:</strong> %s</p>
            </div>
            <p><strong>QR Code:</strong></p>
            <img src=\"%s\" alt=\"QR code\" style=\"width:180px;height:180px;border:1px solid #e5e7eb;border-radius:8px;background:#fff;padding:8px;\" />
            <p>If the QR code does not load, open this link: <a href=\"%s\">%s</a></p>
            """.formatted(escape(studentName), escape(workshopTitle), startTime, escape(roomName),
                escape(qrCode), qrCodeUrl, qrCodeUrl, qrCodeUrl);

        return new NotificationContent(title, wrapHtml(title, body));
    }

    public NotificationContent buildWorkshopCancelled(NotificationRecipient recipient,
                                                      String workshopTitle,
                                                      LocalDateTime startTime,
                                                      String roomName,
                                                      Long price) {
        String title = "Workshop cancelled";
        String apology = "We are very sorry for the inconvenience.";
        String refundNote = "";
        if (price != null && price > 0) {
            refundNote = "<p><strong>Refund notice:</strong> Please bring your registration code and student ID to the Student Affairs Office during business hours for a cash refund.</p>";
        }

        String body = """
            <p>Hi %s,</p>
            <p>Your workshop has been cancelled.</p>
            <div style=\"margin:16px 0;padding:12px;border:1px solid #fee2e2;border-radius:10px;background:#fef2f2;\">
                <p><strong>Workshop:</strong> %s</p>
                <p><strong>Time:</strong> %s</p>
                <p><strong>Room:</strong> %s</p>
            </div>
            <p>%s</p>
            %s
            """.formatted(escape(recipient.fullName()), escape(workshopTitle),
                formatDateTime(startTime), escape(roomName), apology, refundNote);

        return new NotificationContent(title, wrapHtml(title, body));
    }

    public NotificationContent buildWorkshopReminder(NotificationRecipient recipient,
                                                     String workshopTitle,
                                                     LocalDateTime startTime,
                                                     String roomName,
                                                     String layoutMapUrl) {
        String title = "Reminder: workshop starts tomorrow";
        String mapBlock = "";
        if (layoutMapUrl != null && !layoutMapUrl.isBlank()) {
            mapBlock = "<p>Room map: <a href=\"%s\">%s</a></p>".formatted(layoutMapUrl, layoutMapUrl);
        }

        String body = """
            <p>Hi %s,</p>
            <p>This is a friendly reminder that your workshop starts tomorrow.</p>
            <div style=\"margin:16px 0;padding:12px;border:1px solid #e0e7ff;border-radius:10px;background:#eef2ff;\">
                <p><strong>Workshop:</strong> %s</p>
                <p><strong>Time:</strong> %s</p>
                <p><strong>Room:</strong> %s</p>
            </div>
            %s
            <p>Please arrive 10 minutes early for check-in.</p>
            """.formatted(escape(recipient.fullName()), escape(workshopTitle),
                formatDateTime(startTime), escape(roomName), mapBlock);

        return new NotificationContent(title, wrapHtml(title, body));
    }

    public NotificationContent buildCsvSyncCompleted(NotificationRecipient recipient,
                                                     long totalRows,
                                                     long successRows,
                                                     long failedRows,
                                                     long durationSeconds) {
        String title = "Student CSV sync completed";
        String body = """
            <p>Hi %s,</p>
            <p>The nightly CSV sync job has completed.</p>
            <div style=\"margin:16px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f9fafb;\">
                <p><strong>Total rows:</strong> %d</p>
                <p><strong>Successful upserts:</strong> %d</p>
                <p><strong>Failed rows:</strong> %d</p>
                <p><strong>Duration:</strong> %d seconds</p>
            </div>
            <p>Please review the logs for line-level error details if needed.</p>
            """.formatted(escape(recipient.fullName()), totalRows, successRows, failedRows, durationSeconds);

        return new NotificationContent(title, wrapHtml(title, body));
    }

    private String wrapHtml(String heading, String bodyHtml) {
        return """
            <div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827;line-height:1.6;\">
                <h2 style=\"margin:0 0 12px;font-size:18px;color:#111827;\">%s</h2>
                %s
                <hr style=\"margin:24px 0;border:none;border-top:1px solid #e5e7eb;\" />
                <p style=\"font-size:12px;color:#6b7280;\">UniHub Workshop Notifications</p>
            </div>
            """.formatted(escape(heading), bodyHtml);
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "TBD";
        }
        return value.format(DATE_TIME);
    }

    private String buildQrCodeUrl(String qrCode) {
        String code = qrCode == null ? "" : qrCode;
        return "https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=" +
                java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
