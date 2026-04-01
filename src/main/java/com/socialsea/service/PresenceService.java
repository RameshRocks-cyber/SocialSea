package com.socialsea.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PresenceService {

    private final ConcurrentMap<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastSeenByUser = new ConcurrentHashMap<>();

    public void markConnected(String username, String sessionId) {
        String userKey = normalize(username);
        String sessionKey = normalizeSession(sessionId);
        if (userKey.isBlank() || sessionKey.isBlank()) return;

        String previousUser = userBySession.put(sessionKey, userKey);
        if (previousUser != null && !previousUser.isBlank() && !previousUser.equals(userKey)) {
            removeSession(previousUser, sessionKey);
        }

        sessionsByUser.computeIfAbsent(userKey, ignored -> ConcurrentHashMap.newKeySet()).add(sessionKey);
        lastSeenByUser.put(userKey, Instant.now());
    }

    public void markDisconnected(String sessionId) {
        String sessionKey = normalizeSession(sessionId);
        if (sessionKey.isBlank()) return;

        String userKey = userBySession.remove(sessionKey);
        if (userKey == null || userKey.isBlank()) return;

        removeSession(userKey, sessionKey);
        lastSeenByUser.put(userKey, Instant.now());
    }

    public boolean isOnline(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return false;
        Set<String> sessions = sessionsByUser.get(userKey);
        return sessions != null && !sessions.isEmpty();
    }

    public Instant getLastSeenAt(String username) {
        String userKey = normalize(username);
        if (userKey.isBlank()) return null;
        return lastSeenByUser.get(userKey);
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
