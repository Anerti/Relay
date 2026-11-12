package com.techindna.relay.entity;

import com.techindna.relay.entity.enums.EventLogSeverity;
import java.time.Instant;
import java.util.UUID;

public record EventLog(
        UUID id,
        UUID hostId,
        String userAgent,
        EventLogSeverity severity,
        String description,
        Instant createdAt
) {}
