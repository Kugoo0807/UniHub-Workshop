package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.dto.NotificationResponse;
import com.unihub.backend.dto.PageResponse;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.event.CsvSyncCompletedEvent;
import com.unihub.backend.event.WorkshopCancelledEvent;
import com.unihub.backend.event.WorkshopRegistrationSuccessEvent;
import com.unihub.backend.event.WorkshopReminderEvent;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.NotificationRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationTemplateService templateService;

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private TaskExecutor notificationTaskExecutor;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private Registration registration;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id(9L)
                .fullName("Student One")
                .email("student@unihub.edu.vn")
                .phoneNumber("+84901234567")
                .chatId("583920123")
                .build();

        registration = Registration.builder()
                .id(101L)
                .user(user)
                .build();
    }

    @Test
    void getUserNotifications_success_returnsPageResponse() {
        Notification n1 = Notification.builder()
                .id(1L)
                .title("A")
                .contentHtml("<p>A</p>")
                .channel("IN_APP")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();
        Notification n2 = Notification.builder()
                .id(2L)
                .title("B")
                .contentHtml("<p>B</p>")
                .channel("IN_APP")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        Page<Notification> page = new PageImpl<>(List.of(n1, n2), PageRequest.of(0, 10), 2);
        when(notificationRepository.findInAppNotificationsForUser(eq(9L), eq("IN_APP"), any()))
                .thenReturn(page);

        PageResponse<NotificationResponse> response = notificationService.getUserNotifications(9L, 0, 10);

        assertEquals(2, response.getContent().size());
        assertEquals(2, response.getTotalElements());
        assertEquals(1L, response.getContent().get(0).id());
        assertEquals(2L, response.getContent().get(1).id());
        verify(notificationRepository).findInAppNotificationsForUser(eq(9L), eq("IN_APP"), any());
    }

    @Test
    void getUserNotifications_repositoryFailure_throwsException() {
        when(notificationRepository.findInAppNotificationsForUser(eq(9L), eq("IN_APP"), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> notificationService.getUserNotifications(9L, 0, 10));
    }

    @Test
    void handleRegistrationSuccess_success_dispatchesWithTelegram() {
        NotificationContent content = new NotificationContent("Title", "<p>Body</p>");
        when(registrationRepository.findByIdWithUserAndWorkshop(101L)).thenReturn(Optional.of(registration));
        when(templateService.buildRegistrationSuccess(registration)).thenReturn(content);

        notificationService.handleRegistrationSuccess(new WorkshopRegistrationSuccessEvent(101L));

        ArgumentCaptor<List<String>> channelsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(eq(new NotificationRecipient(
                9L, "Student One", "student@unihub.edu.vn", "+84901234567", "583920123"
        )), eq(content), channelsCaptor.capture());

        List<String> channels = channelsCaptor.getValue();
        assertEquals(List.of("EMAIL", "IN_APP", "TELEGRAM"), channels);
    }

    @Test
    void handleRegistrationSuccess_missingRegistration_throwsNotFound() {
        when(registrationRepository.findByIdWithUserAndWorkshop(101L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.handleRegistrationSuccess(new WorkshopRegistrationSuccessEvent(101L)));

        verifyNoInteractions(templateService, dispatcher);
    }

    @Test
    void handleWorkshopCancelled_emptyRecipients_noWork() {
        WorkshopCancelledEvent event = new WorkshopCancelledEvent(
                1L,
                "Title",
                LocalDateTime.now(),
                "Room A",
                0L,
                List.of()
        );

        notificationService.handleWorkshopCancelled(event);

        verifyNoInteractions(notificationTaskExecutor, templateService, dispatcher);
    }

    @Test
    void handleWorkshopCancelled_success_batchesAndDispatchesAsync() {
        NotificationRecipient r1 = new NotificationRecipient(1L, "A", "a@x", "0901234567", "111");
        NotificationRecipient r2 = new NotificationRecipient(2L, "B", "b@x", "+84909998877", "222");

        NotificationContent content = new NotificationContent("Cancel", "<p>cancel</p>");
        when(templateService.buildWorkshopCancelled(eq(r1), any(), any(), any(), any()))
                .thenReturn(content);
        when(templateService.buildWorkshopCancelled(eq(r2), any(), any(), any(), any()))
                .thenReturn(content);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(notificationTaskExecutor).execute(any(Runnable.class));

        WorkshopCancelledEvent event = new WorkshopCancelledEvent(
                1L,
                "Title",
                LocalDateTime.now(),
                "Room A",
                0L,
                List.of(r1, r2)
        );

        notificationService.handleWorkshopCancelled(event);

        verify(templateService).buildWorkshopCancelled(eq(r1), any(), any(), any(), any());
        verify(templateService).buildWorkshopCancelled(eq(r2), any(), any(), any(), any());
        verify(dispatcher).dispatch(eq(r1), eq(content), eq(List.of("EMAIL", "IN_APP", "TELEGRAM")));
        verify(dispatcher).dispatch(eq(r2), eq(content), eq(List.of("EMAIL", "IN_APP", "TELEGRAM")));
    }

    @Test
        void handleWorkshopReminder_missingChatId_excludesTelegram() {
                NotificationRecipient recipient = new NotificationRecipient(1L, "A", "a@x", "invalid", "");
        NotificationContent content = new NotificationContent("Reminder", "<p>R</p>");
        when(templateService.buildWorkshopReminder(eq(recipient), any(), any(), any(), any())).thenReturn(content);

        WorkshopReminderEvent event = new WorkshopReminderEvent(
                1L,
                "Title",
                LocalDateTime.now(),
                "Room A",
                null,
                List.of(recipient)
        );

        notificationService.handleWorkshopReminder(event);

        verify(dispatcher).dispatch(eq(recipient), eq(content), eq(List.of("EMAIL", "IN_APP")));
    }

    @Test
    void handleCsvSyncCompleted_noAdmins_doesNothing() {
        when(userRepository.findRecipientsByRole("ADMIN")).thenReturn(List.of());

        notificationService.handleCsvSyncCompleted(new CsvSyncCompletedEvent(10, 9, 1, 5));

        verify(userRepository).findRecipientsByRole("ADMIN");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(templateService, dispatcher);
    }

    @Test
    void handleCsvSyncCompleted_admins_dispatchesWithoutTelegram() {
        NotificationRecipient admin = new NotificationRecipient(3L, "Admin", "admin@x", "+84901111111", "");
        NotificationContent content = new NotificationContent("Sync", "<p>sync</p>");

        when(userRepository.findRecipientsByRole("ADMIN")).thenReturn(List.of(admin));
        when(templateService.buildCsvSyncCompleted(eq(admin), eq(10L), eq(9L), eq(1L), eq(5L)))
                .thenReturn(content);

        notificationService.handleCsvSyncCompleted(new CsvSyncCompletedEvent(10, 9, 1, 5));

        verify(dispatcher).dispatch(eq(admin), eq(content), eq(List.of("EMAIL", "IN_APP")));
    }
}
