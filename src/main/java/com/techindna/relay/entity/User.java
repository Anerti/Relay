package com.techindna.relay.entity;

import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String email,
        boolean verified,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
