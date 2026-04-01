package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration ONLINE_TTL = Duration.ofMinutes(5);
    private static final Duration PERSIST_THROTTLE = Duration.ofSeconds(15);

    private final UserRepository userRepo;
    private final ConcurrentMap<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastSeenByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastPersistedByUser = new ConcurrentHashMap<>();

    public void markConnected(String username, String sessionId) {
        String userKey = normalize(username);
        String sessionKey = normalizeSession(sessionId);
        if (userKey.isBlank() || sessionKey.isBlank()) return;

        String previousUser = userBySession.put(sessionKey, userKey);
        if (previousUser != null && !previousUser.isBlank() && !previousUser.equals(userKey)) {
            removeSession(previousUser, sessionKey);
        }

        sessionsByUser.computeIfAbsent(userKey, ignored -> ConcurrentHashMap.newKeySet()).add(sessionKey);
        touchInternal(userKey, Instant.now(), false, null);
    }

    public void markDisconnected(String sessionId) {
        String sessionKey = normalizeSession(sessionId);
        if (sessionKey.isBlank()) return;

        String userKey = userBySession.remove(sessionKey);
        if (userKey == null || userKey.isBlank()) return;

        removeSession(userKey, sessionKey);
        touchInternal(userKey, Instant.now(), true, null);
    }

    public void touch(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return;
        touchInternal(userKey, Instant.now(), false, null);
    }

    public void touch(User user) {
        if (user == null) return;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return;
        touchInternal(userKey, Instant.now(), false, user);
    }

    public boolean isOnline(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return false;
        if (hasActiveSession(userKey)) return true;
        User user = userRepo.findByEmail(userKey).orElse(null);
        return isOnline(user);
    }

    public boolean isOnline(User user) {
        if (user == null) return false;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return false;
        if (hasActiveSession(userKey)) return true;
        Instant presenceAt = getLastSeenAt(user);
        return presenceAt != null && presenceAt.isAfter(Instant.now().minus(ONLINE_TTL));
    }

    public Instant getLastSeenAt(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return null;
        User user = userRepo.findByEmail(userKey).orElse(null);
        return latestInstant(lastSeenByUser.get(userKey), toInstant(user != null ? user.getPresenceUpdatedAt() : null));
    }

    public Instant getLastSeenAt(User user) {
        if (user == null) return null;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return null;
        return latestInstant(lastSeenByUser.get(userKey), toInstant(user.getPresenceUpdatedAt()));
    }

    private void touchInternal(String userKey, Instant now, boolean forcePersist, User existingUser) {
        if (userKey.isBlank()) return;
        lastSeenByUser.put(userKey, now);
        persistPresence(userKey, now, forcePersist, existingUser);
    }

    private void persistPresence(String userKey, Instant now, boolean forcePersist, User existingUser) {
        Instant lastPersisted = lastPersistedByUser.get(userKey);
        if (!forcePersist && lastPersisted != null && Duration.between(lastPersisted, now).compareTo(PERSIST_THROTTLE) < 0) {
            return;
        }

        User user = existingUser;
        if (user == null || !userKey.equals(normalize(user.getEmail()))) {
            user = userRepo.findByEmail(userKey).orElse(null);
        }
        if (user == null) return;

        Instant persistedAt = toInstant(user.getPresenceUpdatedAt());
        if (!forcePersist && persistedAt != null && Duration.between(persistedAt, now).compareTo(PERSIST_THROTTLE) < 0) {
            lastPersistedByUser.put(userKey, persistedAt);
            return;
        }

        user.setPresenceUpdatedAt(toLocalDateTime(now));
        userRepo.save(user);
        lastPersistedByUser.put(userKey, now);
    }

    private boolean hasActiveSession(String userKey) {
        Set<String> sessions = sessionsByUser.get(userKey);
        return sessions != null && !sessions.isEmpty();
    }

    private Instant latestInstant(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private void removeSession(String userKey, String sessionKey) {
        sessionsByUser.computeIfPresent(userKey, (ignored, sessions) -> {
            sessions.remove(sessionKey);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String normalizeSession(String sessionId) {
        return sessionId == null ? "" : sessionId.trim();
    }
}
