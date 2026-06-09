package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String KEY_ONLINE_PREFIX = "presence:online:";
    private static final String KEY_LAST_SEEN_PREFIX = "presence:last-seen:";
    private static final String KEY_USER_SESSIONS_PREFIX = "presence:user:sessions:";
    private static final String KEY_SESSION_PREFIX = "presence:session:";

    private final UserRepository userRepo;

    @Autowired(required = false)
    StringRedisTemplate redisTemplate;

    @Value("${app.presence.online-ttl:5m}")
    Duration onlineTtl = Duration.ofMinutes(5);

    @Value("${app.presence.session-ttl:12h}")
    Duration sessionTtl = Duration.ofHours(12);

    @Value("${app.presence.last-seen-ttl:7d}")
    Duration lastSeenTtl = Duration.ofDays(7);

    @Value("${app.presence.persist-throttle:15s}")
    Duration persistThrottle = Duration.ofSeconds(15);

    private final ConcurrentMap<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastSeenByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastPersistedByUser = new ConcurrentHashMap<>();

    public void markConnected(String username, String sessionId) {
        upsertSocketPresence(username, sessionId);
    }

    public void refreshSocket(String username, String sessionId) {
        upsertSocketPresence(username, sessionId);
    }

    public void markDisconnected(String sessionId) {
        String sessionKey = normalizeSession(sessionId);
        if (sessionKey.isBlank()) return;

        String userKey = removeLocalSession(sessionKey);
        if (userKey == null || userKey.isBlank()) {
            userKey = readRedisSessionOwner(sessionKey);
        }
        if (userKey == null || userKey.isBlank()) return;

        removeRedisSession(userKey, sessionKey);
        recordLastSeen(userKey, Instant.now(), true, null);
    }

    public void touch(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return;
        recordLastSeen(userKey, Instant.now(), false, null);
    }

    public void touch(User user) {
        if (user == null) return;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return;
        recordLastSeen(userKey, Instant.now(), false, user);
    }

    public boolean isOnline(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return false;

        if (hasActiveSession(userKey)) return true;

        User user = findUserByEmailSafely(userKey);
        if (user != null) {
            return isOnline(user);
        }

        return isLocallyOnline(userKey);
    }

    public boolean isOnline(User user) {
        if (user == null) return false;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return false;

        if (hasActiveSession(userKey)) return true;

        Instant presenceAt = latestInstant(
                readLastSeenFromRedis(userKey),
                lastSeenByUser.get(userKey),
                toInstant(user.getPresenceUpdatedAt())
        );
        return presenceAt != null && presenceAt.isAfter(Instant.now().minus(onlineTtl));
    }

    public Instant getLastSeenAt(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return null;

        User user = findUserByEmailSafely(userKey);
        return latestInstant(
                readLastSeenFromRedis(userKey),
                lastSeenByUser.get(userKey),
                toInstant(user != null ? user.getPresenceUpdatedAt() : null)
        );
    }

    public Instant getLastSeenAt(User user) {
        if (user == null) return null;
        String userKey = normalize(user.getEmail());
        if (userKey.isBlank()) return null;

        return latestInstant(
                readLastSeenFromRedis(userKey),
                lastSeenByUser.get(userKey),
                toInstant(user.getPresenceUpdatedAt())
        );
    }

    private void upsertSocketPresence(String username, String sessionId) {
        String userKey = normalize(username);
        String sessionKey = normalizeSession(sessionId);
        if (userKey.isBlank() || sessionKey.isBlank()) return;

        Instant now = Instant.now();
        associateLocalSession(userKey, sessionKey);
        writeRedisSocketPresence(userKey, sessionKey);
        recordLastSeen(userKey, now, false, null);
    }

    private void recordLastSeen(String userKey, Instant now, boolean forcePersist, User existingUser) {
        if (userKey.isBlank() || now == null) return;
        lastSeenByUser.put(userKey, now);
        writeRedisLastSeen(userKey, now);
        persistPresence(userKey, now, forcePersist, existingUser);
    }

    private void persistPresence(String userKey, Instant now, boolean forcePersist, User existingUser) {
        Instant lastPersisted = lastPersistedByUser.get(userKey);
        if (!forcePersist && lastPersisted != null && Duration.between(lastPersisted, now).compareTo(persistThrottle) < 0) {
            return;
        }

        User user = existingUser;
        if (user == null || !userKey.equals(normalize(user.getEmail()))) {
            user = findUserByEmailSafely(userKey);
        }
        if (user == null) return;

        Instant persistedAt = toInstant(user.getPresenceUpdatedAt());
        if (!forcePersist && persistedAt != null && Duration.between(persistedAt, now).compareTo(persistThrottle) < 0) {
            lastPersistedByUser.put(userKey, persistedAt);
            return;
        }

        try {
            user.setPresenceUpdatedAt(toLocalDateTime(now));
            userRepo.save(user);
            lastPersistedByUser.put(userKey, now);
        } catch (Exception ex) {
            log.debug("Unable to persist presence for {}: {}", userKey, ex.getMessage());
        }
    }

    private void associateLocalSession(String userKey, String sessionKey) {
        String previousUser = userBySession.put(sessionKey, userKey);
        if (previousUser != null && !previousUser.isBlank() && !previousUser.equals(userKey)) {
            sessionsByUser.computeIfPresent(previousUser, (ignored, sessions) -> {
                sessions.remove(sessionKey);
                return sessions.isEmpty() ? null : sessions;
            });
        }

        sessionsByUser.computeIfAbsent(userKey, ignored -> ConcurrentHashMap.newKeySet()).add(sessionKey);
    }

    private String removeLocalSession(String sessionKey) {
        String userKey = userBySession.remove(sessionKey);
        if (userKey == null || userKey.isBlank()) {
            return null;
        }

        sessionsByUser.computeIfPresent(userKey, (ignored, sessions) -> {
            sessions.remove(sessionKey);
            return sessions.isEmpty() ? null : sessions;
        });

        return userKey;
    }

    private void writeRedisSocketPresence(String userKey, String sessionKey) {
        if (redisTemplate == null) return;

        try {
            String previousUser = redisTemplate.opsForValue().get(redisSessionKey(sessionKey));
            if (previousUser != null && !previousUser.isBlank() && !previousUser.equals(userKey)) {
                redisTemplate.opsForSet().remove(redisUserSessionsKey(previousUser), sessionKey);
            }

            redisTemplate.opsForValue().set(redisSessionKey(sessionKey), userKey, sessionTtl);
            redisTemplate.opsForSet().add(redisUserSessionsKey(userKey), sessionKey);
            redisTemplate.expire(redisUserSessionsKey(userKey), sessionTtl);
            redisTemplate.expire(redisSessionKey(sessionKey), sessionTtl);
        } catch (Exception ex) {
            log.debug("Redis presence update unavailable for {} / {}: {}", userKey, sessionKey, ex.getMessage());
        }
    }

    private void removeRedisSession(String userKey, String sessionKey) {
        if (redisTemplate == null) return;

        try {
            redisTemplate.opsForSet().remove(redisUserSessionsKey(userKey), sessionKey);
            redisTemplate.delete(redisSessionKey(sessionKey));
            Long remaining = redisTemplate.opsForSet().size(redisUserSessionsKey(userKey));
            if (remaining == null || remaining <= 0L) {
                redisTemplate.delete(redisUserSessionsKey(userKey));
            }
        } catch (Exception ex) {
            log.debug("Redis presence remove unavailable for {} / {}: {}", userKey, sessionKey, ex.getMessage());
        }
    }

    private void writeRedisLastSeen(String userKey, Instant now) {
        if (redisTemplate == null) return;

        try {
            redisTemplate.opsForValue().set(redisLastSeenKey(userKey), now.toString(), lastSeenTtl);
        } catch (Exception ex) {
            log.debug("Redis last-seen update unavailable for {}: {}", userKey, ex.getMessage());
        }
    }

    private Instant readLastSeenFromRedis(String userKey) {
        if (redisTemplate == null) return null;

        try {
            String value = redisTemplate.opsForValue().get(redisLastSeenKey(userKey));
            return parseInstant(value);
        } catch (Exception ex) {
            log.debug("Redis last-seen read unavailable for {}: {}", userKey, ex.getMessage());
            return null;
        }
    }

    private String readRedisSessionOwner(String sessionKey) {
        if (redisTemplate == null) return null;

        try {
            String owner = redisTemplate.opsForValue().get(redisSessionKey(sessionKey));
            return normalize(owner);
        } catch (Exception ex) {
            log.debug("Redis session owner lookup unavailable for {}: {}", sessionKey, ex.getMessage());
            return null;
        }
    }

    private boolean hasActiveSession(String userKey) {
        if (redisTemplate != null) {
            try {
                Set<String> sessions = redisTemplate.opsForSet().members(redisUserSessionsKey(userKey));
                if (sessions != null && !sessions.isEmpty()) {
                    List<String> stale = new ArrayList<>();
                    boolean active = false;
                    for (String sessionKey : sessions) {
                        if (sessionKey == null || sessionKey.isBlank()) {
                            stale.add(sessionKey);
                            continue;
                        }
                        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisSessionKey(sessionKey)))) {
                            active = true;
                            continue;
                        }
                        stale.add(sessionKey);
                    }
                    if (!stale.isEmpty()) {
                        redisTemplate.opsForSet().remove(redisUserSessionsKey(userKey), stale.toArray());
                        Long remaining = redisTemplate.opsForSet().size(redisUserSessionsKey(userKey));
                        if (remaining == null || remaining <= 0L) {
                            redisTemplate.delete(redisUserSessionsKey(userKey));
                        }
                    }
                    return active;
                }
                return false;
            } catch (Exception ex) {
                log.debug("Redis session check unavailable for {}: {}", userKey, ex.getMessage());
                return hasLocalActiveSession(userKey);
            }
        }

        return hasLocalActiveSession(userKey);
    }

    private boolean hasLocalActiveSession(String userKey) {
        Set<String> sessions = sessionsByUser.get(userKey);
        return sessions != null && !sessions.isEmpty();
    }

    private boolean isLocallyOnline(String userKey) {
        if (hasLocalActiveSession(userKey)) return true;
        User localUser = findUserByEmailSafely(userKey);
        Instant presenceAt = latestInstant(
                lastSeenByUser.get(userKey),
                toInstant(localUser != null ? localUser.getPresenceUpdatedAt() : null)
        );
        return presenceAt != null && presenceAt.isAfter(Instant.now().minus(onlineTtl));
    }

    private Instant latestInstant(Instant... values) {
        Instant latest = null;
        for (Instant value : values) {
            if (value == null) continue;
            if (latest == null || value.isAfter(latest)) {
                latest = value;
            }
        }
        return latest;
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String normalizeSession(String sessionId) {
        return sessionId == null ? "" : sessionId.trim();
    }

    private String redisUserSessionsKey(String userKey) {
        return KEY_USER_SESSIONS_PREFIX + normalize(userKey);
    }

    private String redisSessionKey(String sessionKey) {
        return KEY_SESSION_PREFIX + normalizeSession(sessionKey);
    }

    private String redisLastSeenKey(String userKey) {
        return KEY_LAST_SEEN_PREFIX + normalize(userKey);
    }

    private User findUserByEmailSafely(String userKey) {
        try {
            return userRepo.findByEmailIgnoreCase(userKey).orElse(null);
        } catch (Exception ex) {
            log.debug("Unable to load user {} for presence lookup: {}", userKey, ex.getMessage());
            return null;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
