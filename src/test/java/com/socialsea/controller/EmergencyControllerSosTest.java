package com.socialsea.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsea.model.EmergencyAlert;
import com.socialsea.model.User;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.EmailService;
import com.socialsea.service.NotificationService;
import com.socialsea.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmergencyControllerSosTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmergencyController controller;

    @Mock
    private UserRepository userRepo;

    @Mock
    private EmergencyAlertRepository emergencyRepo;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UploadService uploadService;

    @Mock
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "locationStaleMinutes", 180L);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "http://frontend.local");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void triggerCreatesAlertAndDispatchesNearbyUsers() throws Exception {
        User reporter = user(1L, "reporter@example.com", "Reporter", 10.0, 20.0);
        User nearby = user(2L, "nearby@example.com", "Nearby Helper", 10.0, 20.0);

        when(userRepo.findByEmail(reporter.getEmail())).thenReturn(Optional.of(reporter));
        when(userRepo.findAll()).thenReturn(List.of(reporter, nearby));
        when(emergencyRepo.save(any(EmergencyAlert.class))).thenAnswer(invocation -> {
            EmergencyAlert saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        mockMvc.perform(post("/api/emergency/trigger")
                        .principal(auth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", 10.0,
                                "longitude", 20.0,
                                "accuracyMeters", 3.0,
                                "radiusMeters", 500,
                                "frontCameraEnabled", true,
                                "backCameraEnabled", false,
                                "audioActive", true,
                                "videoActive", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(77))
                .andExpect(jsonPath("$.notifiedUsers").value(1))
                .andExpect(jsonPath("$.selfNotified").value(true))
                .andExpect(jsonPath("$.radiusMeters").value(500))
                .andExpect(jsonPath("$.liveUrl").value("http://frontend.local/sos/live/77"))
                .andExpect(jsonPath("$.navigateUrl").value("http://frontend.local/sos/navigate/77"))
                .andExpect(jsonPath("$.audioActive").value(true))
                .andExpect(jsonPath("$.videoActive").value(false))
                .andExpect(jsonPath("$.nearbyCount").value(1))
                .andExpect(jsonPath("$.nearbyUsers[0].email").value("nearby@example.com"))
                .andExpect(jsonPath("$.nearbyUsers[0].distanceMeters").value(0))
                .andExpect(jsonPath("$.location.latitude").value(10.0))
                .andExpect(jsonPath("$.location.longitude").value(20.0));

        verify(userRepo).findByEmail(reporter.getEmail());
        verify(userRepo).save(reporter);
        verify(userRepo).findAll();
        verify(notificationService).notifyUserInApp(
                eq("reporter@example.com"),
                eq("Your SOS Is Active"),
                contains("Live page: http://frontend.local/sos/live/77"),
                eq("EMERGENCY")
        );
        verify(notificationService).notifyUserInApp(
                eq("nearby@example.com"),
                eq("Emergency Alert Nearby"),
                anyString(),
                eq("EMERGENCY")
        );
        verify(notificationService).notify(
                eq("Emergency Alert"),
                contains("Notified nearby users: 1"),
                eq("EMERGENCY")
        );
        verify(emailService).sendEmergency(
                eq("nearby@example.com"),
                eq("Emergency Alert Nearby"),
                contains("Emergency alert by reporter@example.com")
        );
        verify(emergencyRepo).save(any(EmergencyAlert.class));
    }

    @Test
    void triggerRejectsMissingCoordinates() throws Exception {
        mockMvc.perform(post("/api/emergency/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", 10.0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("latitude and longitude are required"));

        verifyNoInteractions(userRepo, emergencyRepo, notificationService, uploadService, emailService);
    }

    @Test
    void presenceUpdatesKnownUserLocationFromReporterEmail() throws Exception {
        User reporter = user(3L, "helper@example.com", "Helper", null, null);
        when(userRepo.findByEmail(reporter.getEmail())).thenReturn(Optional.of(reporter));

        mockMvc.perform(post("/api/emergency/presence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", 12.34,
                                "longitude", 56.78,
                                "reporterEmail", reporter.getEmail()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getLastLatitude()).isEqualTo(12.34);
        assertThat(captor.getValue().getLastLongitude()).isEqualTo(56.78);
        assertThat(captor.getValue().getLocationUpdatedAt()).isNotNull();
        verify(userRepo).findByEmail(reporter.getEmail());
    }

    @Test
    void assistReturnsNavigationPayloadForNearbyViewer() throws Exception {
        User viewer = user(4L, "viewer@example.com", "Viewer", 10.0, 20.0);
        EmergencyAlert alert = alert(91L, "reporter@example.com", 10.0, 20.0, 500);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(emergencyRepo.findById(91L)).thenReturn(Optional.of(alert));

        mockMvc.perform(get("/api/emergency/91/assist")
                        .principal(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(91))
                .andExpect(jsonPath("$.reporterEmail").value("reporter@example.com"))
                .andExpect(jsonPath("$.latitude").value(10.0))
                .andExpect(jsonPath("$.longitude").value(20.0))
                .andExpect(jsonPath("$.mapsUrl").value("https://www.google.com/maps/dir/?api=1&destination=10.0,20.0"))
                .andExpect(jsonPath("$.liveUrl").value("http://frontend.local/sos/live/91"))
                .andExpect(jsonPath("$.navigateUrl").value("http://frontend.local/sos/navigate/91"));

        verify(userRepo).findByEmail(viewer.getEmail());
        verify(emergencyRepo).findById(91L);
        verifyNoInteractions(uploadService);
    }

    @Test
    void assistReturnsNotFoundForUnknownAlertId() throws Exception {
        User viewer = user(5L, "viewer@example.com", "Viewer", 10.0, 20.0);
        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(emergencyRepo.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/emergency/999/assist")
                        .principal(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found"));

        verify(userRepo).findByEmail(viewer.getEmail());
        verify(emergencyRepo).findById(999L);
        verifyNoInteractions(notificationService, uploadService, emailService);
    }

    @Test
    void assistRejectsViewerOutsideAlertRadius() throws Exception {
        User viewer = user(5L, "viewer@example.com", "Viewer", 0.0, 0.0);
        EmergencyAlert alert = alert(92L, "reporter@example.com", 10.0, 20.0, 500);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(emergencyRepo.findById(92L)).thenReturn(Optional.of(alert));

        mockMvc.perform(get("/api/emergency/92/assist")
                        .principal(auth(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not allowed"));

        verify(userRepo).findByEmail(viewer.getEmail());
        verify(emergencyRepo).findById(92L);
        verifyNoInteractions(notificationService, uploadService, emailService);
    }

    @Test
    void assistRejectsStaleViewerLocation() throws Exception {
        User viewer = user(7L, "viewer@example.com", "Viewer", 10.0, 20.0);
        viewer.setLocationUpdatedAt(LocalDateTime.now().minusHours(8));
        EmergencyAlert alert = alert(94L, "reporter@example.com", 10.0, 20.0, 500);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(emergencyRepo.findById(94L)).thenReturn(Optional.of(alert));

        mockMvc.perform(get("/api/emergency/94/assist")
                        .principal(auth(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not allowed"));

        verify(userRepo).findByEmail(viewer.getEmail());
        verify(emergencyRepo).findById(94L);
        verifyNoInteractions(notificationService, uploadService, emailService);
    }

    @Test
    void heartbeatRejectsStoppedAlerts() throws Exception {
        User reporter = user(6L, "reporter@example.com", "Reporter", 10.0, 20.0);
        EmergencyAlert alert = alert(93L, reporter.getEmail(), 10.0, 20.0, 500);
        alert.setActive(false);

        when(emergencyRepo.findById(93L)).thenReturn(Optional.of(alert));

        mockMvc.perform(post("/api/emergency/93/heartbeat")
                        .principal(auth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alert already stopped"));

        verify(emergencyRepo).findById(93L);
        verify(emergencyRepo, never()).save(any(EmergencyAlert.class));
        verifyNoInteractions(userRepo, notificationService, uploadService, emailService);
    }

    @Test
    void heartbeatRejectsNonReporter() throws Exception {
        User intruder = user(8L, "intruder@example.com", "Intruder", 10.0, 20.0);
        EmergencyAlert alert = alert(95L, "reporter@example.com", 10.0, 20.0, 500);

        when(emergencyRepo.findById(95L)).thenReturn(Optional.of(alert));

        mockMvc.perform(post("/api/emergency/95/heartbeat")
                        .principal(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not allowed"));

        verify(emergencyRepo).findById(95L);
        verify(emergencyRepo, never()).save(any(EmergencyAlert.class));
        verifyNoInteractions(userRepo, notificationService, uploadService, emailService);
    }

    @Test
    void activeAlertsIncludesNearbyUsersForVisibleAlert() throws Exception {
        User reporter = user(10L, "reporter@example.com", "Reporter", 10.0, 20.0);
        User nearby = user(11L, "nearby@example.com", "Nearby Helper", 10.0, 20.0);
        User stale = user(12L, "stale@example.com", "Stale Helper", 10.0, 20.0);
        stale.setLocationUpdatedAt(LocalDateTime.now().minusHours(8));

        EmergencyAlert alert = alert(201L, reporter.getEmail(), 10.0, 20.0, 500);
        when(emergencyRepo.findTop20ByActiveTrueOrderByStartedAtDesc()).thenReturn(List.of(alert));
        when(userRepo.findAll()).thenReturn(List.of(reporter, nearby, stale));

        mockMvc.perform(get("/api/emergency/active")
                        .param("latitude", "10.0")
                        .param("longitude", "20.0")
                        .param("includeNearby", "true")
                        .principal(auth(user(20L, "viewer@example.com", "Viewer", 0.0, 0.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value(201))
                .andExpect(jsonPath("$[0].reporterEmail").value("reporter@example.com"))
                .andExpect(jsonPath("$[0].latitude").value(10.0))
                .andExpect(jsonPath("$[0].longitude").value(20.0))
                .andExpect(jsonPath("$[0].liveUrl").value("http://frontend.local/sos/live/201"))
                .andExpect(jsonPath("$[0].navigateUrl").value("http://frontend.local/sos/navigate/201"))
                .andExpect(jsonPath("$[0].nearbyCount").value(1))
                .andExpect(jsonPath("$[0].nearbyUsers[0].email").value("nearby@example.com"))
                .andExpect(jsonPath("$[0].nearbyUsers[0].distanceMeters").value(0))
                .andExpect(jsonPath("$[0].nearbyUsers[0].name").value("Nearby Helper"));

        verify(emergencyRepo).findTop20ByActiveTrueOrderByStartedAtDesc();
        verify(userRepo).findAll();
    }

    @Test
    void heartbeatUpdatesAlertAndReporterState() throws Exception {
        User reporter = user(30L, "reporter@example.com", "Reporter", 10.0, 20.0);
        EmergencyAlert alert = alert(301L, reporter.getEmail(), 10.0, 20.0, 500);
        alert.setCurrentLatitude(10.0);
        alert.setCurrentLongitude(20.0);

        when(emergencyRepo.findById(301L)).thenReturn(Optional.of(alert));
        when(userRepo.findByEmail(reporter.getEmail())).thenReturn(Optional.of(reporter));
        when(emergencyRepo.save(any(EmergencyAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/emergency/301/heartbeat")
                        .principal(auth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", 11.11,
                                "longitude", 22.22,
                                "audioActive", true,
                                "videoActive", false,
                                "previewFrame", "frame-1",
                                "previewFrameAt", "2026-06-12T01:10:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(301))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.audioActive").value(true))
                .andExpect(jsonPath("$.videoActive").value(false))
                .andExpect(jsonPath("$.lastHeartbeatAt").exists());

        ArgumentCaptor<EmergencyAlert> alertCaptor = ArgumentCaptor.forClass(EmergencyAlert.class);
        verify(emergencyRepo).save(alertCaptor.capture());
        EmergencyAlert saved = alertCaptor.getValue();
        assertThat(saved.getCurrentLatitude()).isEqualTo(11.11);
        assertThat(saved.getCurrentLongitude()).isEqualTo(22.22);
        assertThat(saved.isLiveAudioActive()).isTrue();
        assertThat(saved.isLiveVideoActive()).isFalse();
        assertThat(saved.getLastPreviewFrame()).isEqualTo("frame-1");
        assertThat(saved.getLastPreviewFrameAt()).isEqualTo("2026-06-12T01:10:00");
        assertThat(saved.getLastHeartbeatAt()).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLatitude()).isEqualTo(11.11);
        assertThat(userCaptor.getValue().getLastLongitude()).isEqualTo(22.22);
        assertThat(userCaptor.getValue().getLocationUpdatedAt()).isNotNull();
    }

    @Test
    void stopDisablesAlertAndStoresUploadedRecording() throws Exception {
        User reporter = user(40L, "reporter@example.com", "Reporter", 10.0, 20.0);
        EmergencyAlert alert = alert(401L, reporter.getEmail(), 10.0, 20.0, 500);
        when(emergencyRepo.findById(401L)).thenReturn(Optional.of(alert));
        when(uploadService.upload(any())).thenReturn("https://cdn.example.com/sos/401.webm");
        when(emergencyRepo.save(any(EmergencyAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile media = new MockMultipartFile(
                "media",
                "sos.webm",
                "video/webm",
                new byte[] {1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/emergency/401/stop")
                        .file(media)
                        .param("durationMs", "1234")
                        .principal(auth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(401))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.mediaUrl").value("https://cdn.example.com/sos/401.webm"))
                .andExpect(jsonPath("$.durationMs").value(1234))
                .andExpect(jsonPath("$.audioActive").value(false))
                .andExpect(jsonPath("$.videoActive").value(false));

        ArgumentCaptor<EmergencyAlert> alertCaptor = ArgumentCaptor.forClass(EmergencyAlert.class);
        verify(emergencyRepo).save(alertCaptor.capture());
        EmergencyAlert saved = alertCaptor.getValue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getDurationMs()).isEqualTo(1234L);
        assertThat(saved.isLiveAudioActive()).isFalse();
        assertThat(saved.isLiveVideoActive()).isFalse();
        assertThat(saved.getMediaUrl()).isEqualTo("https://cdn.example.com/sos/401.webm");
        assertThat(saved.getEndedAt()).isNotNull();
        assertThat(saved.getLastHeartbeatAt()).isNotNull();
        verify(uploadService).upload(any());
    }

    @Test
    void stopRejectsNonReporter() throws Exception {
        User intruder = user(9L, "intruder@example.com", "Intruder", 10.0, 20.0);
        EmergencyAlert alert = alert(402L, "reporter@example.com", 10.0, 20.0, 500);
        when(emergencyRepo.findById(402L)).thenReturn(Optional.of(alert));

        mockMvc.perform(multipart("/api/emergency/402/stop")
                        .param("durationMs", "500")
                        .principal(auth(intruder)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not allowed"));

        verify(emergencyRepo).findById(402L);
        verify(emergencyRepo, never()).save(any(EmergencyAlert.class));
        verifyNoInteractions(userRepo, notificationService, uploadService, emailService);
    }

    @Test
    void stopRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(multipart("/api/emergency/404/stop")
                        .param("durationMs", "99"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Login required"));

        verifyNoInteractions(userRepo, emergencyRepo, notificationService, uploadService, emailService);
    }

    private static User user(Long id, String email, String name, Double latitude, Double longitude) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
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
        alert.setStartedAt(LocalDateTime.now().minusMinutes(2));
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
