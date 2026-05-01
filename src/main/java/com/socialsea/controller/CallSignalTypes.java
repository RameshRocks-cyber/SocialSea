package com.socialsea.controller;

import java.util.Set;

public final class CallSignalTypes {

    private CallSignalTypes() {}

    public static final Set<String> ALLOWED = Set.of(
            "offer", "answer", "ice", "hangup", "reject", "busy", "ringing",
            "livekit-invite", "livekit-accept",
            "connected", "refreshing", "ended", "accepted", "typing"
    );
}
