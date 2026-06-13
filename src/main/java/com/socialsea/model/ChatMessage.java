package com.socialsea.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ChatGroup group;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(length = 1200)
    private String audioUrl;

    @Column(length = 1200)
    private String mediaUrl;

    @Column(length = 40)
    private String mediaType;

    @Column(length = 255)
    private String fileName;

    @Column(length = 120)
    private String clientMessageId;

    @Column
    private Long mediaSizeBytes;

    @Column(length = 64)
    private String mediaFingerprint;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime deliveredAt;

    @Column
    private LocalDateTime readAt;

    @Column
    private LocalDateTime senderDeletedAt;

    @Column
    private LocalDateTime receiverDeletedAt;

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public ChatGroup getGroup() {
        return group;
    }

    public void setGroup(ChatGroup group) {
        this.group = group;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public Long getMediaSizeBytes() {
        return mediaSizeBytes;
    }

    public void setMediaSizeBytes(Long mediaSizeBytes) {
        this.mediaSizeBytes = mediaSizeBytes;
    }

    public String getMediaFingerprint() {
        return mediaFingerprint;
    }

    public void setMediaFingerprint(String mediaFingerprint) {
        this.mediaFingerprint = mediaFingerprint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getSenderDeletedAt() {
        return senderDeletedAt;
    }

    public void setSenderDeletedAt(LocalDateTime senderDeletedAt) {
        this.senderDeletedAt = senderDeletedAt;
    }

    public LocalDateTime getReceiverDeletedAt() {
        return receiverDeletedAt;
    }

    public void setReceiverDeletedAt(LocalDateTime receiverDeletedAt) {
        this.receiverDeletedAt = receiverDeletedAt;
    }
}
