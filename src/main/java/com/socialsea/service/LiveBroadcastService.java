package com.socialsea.service;

import com.socialsea.dto.LiveBroadcastDto;
import org.springframework.stereotype.Service;

@Service
public class LiveBroadcastService {
    private LiveBroadcastDto active;
    private String activeHostId;

    public synchronized LiveBroadcastDto getActive() {
        if (active == null) return null;
        Long expiresAt = active.getExpiresAt();
        if (expiresAt != null && expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            active = null;
            activeHostId = null;
            return null;
        }
        return active;
    }

    public synchronized LiveBroadcastDto start(LiveBroadcastDto payload, String hostId) {
        long now = System.currentTimeMillis();
        LiveBroadcastDto next = new LiveBroadcastDto();
        if (payload != null && payload.getId() != null) {
            next.setId(payload.getId());
        } else {
            next.setId(now);
        }

        String title = payload != null ? payload.getTitle() : null;
        if (title == null || title.isBlank()) {
            title = "Live now";
        }
        next.setTitle(title.trim());

        String hostName = payload != null ? payload.getHostName() : null;
        if (hostName == null || hostName.isBlank()) {
            hostName = hostId;
        }
        next.setHostName(hostName != null ? hostName.trim() : null);

        if (payload != null) {
            next.setLanguage(payload.getLanguage());
            next.setFilter(payload.getFilter());
            next.setScreenSharing(payload.isScreenSharing());
        }

        next.setStartedAt(now);
        long expiresAt = now + 2L * 60L * 60L * 1000L;
        if (payload != null && payload.getExpiresAt() != null && payload.getExpiresAt() > now) {
            expiresAt = payload.getExpiresAt();
        }
        next.setExpiresAt(expiresAt);
        next.setActive(true);

        active = next;
        activeHostId = hostId;
        return next;
    }

    public synchronized void stop(String hostId) {
        if (active == null) return;
        if (activeHostId == null || hostId == null || hostId.equals(activeHostId)) {
            active = null;
            activeHostId = null;
        }
    }
}
