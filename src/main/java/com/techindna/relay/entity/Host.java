package com.techindna.relay.entity;

import com.techindna.relay.entity.enums.HostStatus;
import java.time.Instant;
import java.util.UUID;

public record Host(
        UUID id,
        String ipAddress,
        HostStatus status,
        Instant lastSeenAt,
        Instant updatedAt
) {}
