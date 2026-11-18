package com.techindna.relay.entity;

import com.techindna.relay.entity.enums.MessageIssuer;
import java.time.Instant;
import java.util.UUID;

public record Message(
        UUID id,
        UUID senderId,
        UUID receiverId,
        UUID replyTo,
        MessageIssuer issuer,
        String content,
        Instant createdAt,
        Instant updatedAt
) {}
