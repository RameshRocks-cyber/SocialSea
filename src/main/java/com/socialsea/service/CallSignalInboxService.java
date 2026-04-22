package com.socialsea.service;

import com.socialsea.dto.CallSignalDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class CallSignalInboxService {

    private static final long KEEP_MS = 90_000;
    private static final int MAX_PER_USER = 200;

    private final Map<Long, Queue<CallSignalDto>> inboxByUserId = new ConcurrentHashMap<>();

    public void enqueue(Long targetUserId, CallSignalDto signal) {
        if (targetUserId == null || signal == null) return;
        Queue<CallSignalDto> queue = inboxByUserId.computeIfAbsent(targetUserId, ignored -> new ConcurrentLinkedQueue<>());
        queue.offer(signal);
        prune(queue, System.currentTimeMillis());
    }

    public List<CallSignalDto> drain(Long userId) {
        if (userId == null) return List.of();
        Queue<CallSignalDto> queue = inboxByUserId.get(userId);
        if (queue == null) return List.of();
        long now = System.currentTimeMillis();
        prune(queue, now);
        List<CallSignalDto> items = new ArrayList<>(queue);
        queue.clear();
        return items;
    }

    public List<CallSignalDto> drainNonTyping(Long userId) {
        if (userId == null) return List.of();
        Queue<CallSignalDto> queue = inboxByUserId.get(userId);
        if (queue == null) return List.of();
        long now = System.currentTimeMillis();
        prune(queue, now);

        List<CallSignalDto> snapshot = new ArrayList<>(queue);
        List<CallSignalDto> items = new ArrayList<>();
        for (CallSignalDto item : snapshot) {
            if (item == null || isTypingSignal(item)) continue;
            if (queue.remove(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private boolean isTypingSignal(CallSignalDto signal) {
        if (signal == null) return false;
        String type = signal.getType();
        return type != null && "typing".equalsIgnoreCase(type.trim());
    }

    private void prune(Queue<CallSignalDto> queue, long now) {
        queue.removeIf(item -> item == null || item.getTimestamp() <= 0 || (now - item.getTimestamp()) > KEEP_MS);
        while (queue.size() > MAX_PER_USER) {
            queue.poll();
        }
    }
}
