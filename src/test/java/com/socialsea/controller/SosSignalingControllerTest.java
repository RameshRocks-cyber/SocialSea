package com.socialsea.controller;

import com.socialsea.model.EmergencyAlert;
import com.socialsea.model.User;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SosSignalingControllerTest {

    @InjectMocks
    private SosSignalingController controller;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EmergencyAlertRepository emergencyRepo;

    @Mock
    private UserRepository userRepo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "locationStaleMinutes", 180L);
    }

    @Test
    void signalRelaysRtcOfferFromNearbyViewer() {
        User viewer = user(21L, "viewer@example.com", 10.0, 20.0);
        EmergencyAlert alert = alert(55L, "reporter@example.com", 10.0, 20.0, 500);

        when(emergencyRepo.findById(55L)).thenReturn(Optional.of(alert));
        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RTC-Offer");
        payload.put("sdp", "offer-sdp");
        payload.put("candidate", "ice-candidate");

        controller.signal(55L, payload, auth(viewer));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sos/55"), captor.capture());

        Map<String, Object> outbound = captor.getValue();
        assertThat(outbound).containsEntry("type", "rtc-offer");
        assertThat(outbound).containsEntry("alertId", 55L);
        assertThat(outbound).containsEntry("fromEmail", viewer.getEmail());
        assertThat(outbound).containsEntry("sdp", "offer-sdp");
        assertThat(outbound).containsEntry("candidate", "ice-candidate");
        assertThat(((Number) outbound.get("timestamp")).longValue()).isPositive();
    }

    @Test
    void signalIgnoresUnauthenticatedRequests() {
        controller.signal(55L, Map.of("type", "rtc-offer"), null);

        verifyNoInteractions(messagingTemplate, emergencyRepo, userRepo);
    }

    @Test
    void signalIgnoresNullPayload() {
        User viewer = user(24L, "viewer@example.com", 10.0, 20.0);

        controller.signal(55L, null, auth(viewer));

        verifyNoInteractions(messagingTemplate, emergencyRepo, userRepo);
    }

    @Test
    void signalIgnoresNullAlertId() {
        User viewer = user(25L, "viewer@example.com", 10.0, 20.0);

        controller.signal(null, Map.of("type", "rtc-candidate"), auth(viewer));

        verifyNoInteractions(messagingTemplate, emergencyRepo, userRepo);
    }

    @Test
    void signalIgnoresUnknownAlertIdWithoutPublishing() {
        User viewer = user(26L, "viewer@example.com", 10.0, 20.0);

        when(emergencyRepo.findById(999L)).thenReturn(Optional.empty());

        controller.signal(999L, Map.of("type", "rtc-stop"), auth(viewer));

        verify(emergencyRepo).findById(999L);
        verifyNoInteractions(messagingTemplate, userRepo);
    }

    @Test
    void signalRejectsStaleViewerLocation() {
        User viewer = user(27L, "viewer@example.com", 10.0, 20.0);
        viewer.setLocationUpdatedAt(LocalDateTime.now().minusHours(8));
        EmergencyAlert alert = alert(58L, "reporter@example.com", 10.0, 20.0, 500);

        when(emergencyRepo.findById(58L)).thenReturn(Optional.of(alert));
        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));

        controller.signal(58L, Map.of("type", "rtc-candidate"), auth(viewer));

        verify(emergencyRepo).findById(58L);
        verify(userRepo).findByEmail(viewer.getEmail());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void signalRejectsUnsupportedType() {
        User viewer = user(22L, "viewer@example.com", 10.0, 20.0);
        EmergencyAlert alert = alert(56L, "reporter@example.com", 10.0, 20.0, 500);

        when(emergencyRepo.findById(56L)).thenReturn(Optional.of(alert));
        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));

        controller.signal(56L, Map.of("type", "not-supported"), auth(viewer));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void signalRejectsFarAwayViewerEvenWithValidType() {
        User viewer = user(23L, "viewer@example.com", 0.0, 0.0);
        EmergencyAlert alert = alert(57L, "reporter@example.com", 10.0, 20.0, 500);

        when(emergencyRepo.findById(57L)).thenReturn(Optional.of(alert));
        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));

        controller.signal(57L, Map.of("type", "rtc-answer"), auth(viewer));

        verifyNoInteractions(messagingTemplate);
    }

    private static User user(Long id, String email, Double latitude, Double longitude) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setLastLatitude(latitude);
        user.setLastLongitude(longitude);
        user.setLocationUpdatedAt(LocalDateTime.now());
        return user;
    }

    private static EmergencyAlert alert(Long id, String reporterEmail, Double latitude, Double longitude, Integer radiusMeters) {
        EmergencyAlert alert = new EmergencyAlert();
        alert.setId(id);
        alert.setReporterEmail(reporterEmail);
        alert.setLatitude(latitude);
        alert.setLongitude(longitude);
        alert.setCurrentLatitude(latitude);
        alert.setCurrentLongitude(longitude);
        alert.setRadiusMeters(radiusMeters);
        alert.setActive(true);
        return alert;
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
