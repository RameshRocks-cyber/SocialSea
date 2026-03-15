package com.socialsea.dto;

import lombok.Data;

@Data
public class CallSignalDto {
    private String type;
    private Long fromUserId;
    private Long toUserId;
    private String fromName;
    private String fromEmail;
    private String mode;
    private String roomId;
    private boolean group;
    private java.util.List<Long> groupMembers;
    private String sdp;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
    private Long timestamp;
}
