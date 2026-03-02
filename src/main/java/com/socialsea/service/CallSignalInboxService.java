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

    private final Map<Long, Queue<CallSignalDto>> inboxByUserId = new ConcurrentHashMap<>();

    public void enqueue(Long targetUserId, CallSignalDto signal) {
        if (targetUserId == null || signal == null) return;
        inboxByUserId.computeIfAbsent(targetUserId, ignored -> new ConcurrentLinkedQueue<>()).offer(signal);
    }

    public List<CallSignalDto> drain(Long userId) {
        if (userId == null) return List.of();
        Queue<CallSignalDto> queue = inboxByUserId.get(userId);
        if (queue == null) return List.of();
        List<CallSignalDto> out = new ArrayList<>();
        CallSignalDto next;
        while ((next = queue.poll()) != null) {
            out.add(next);
        }
        return out;
    }
}
