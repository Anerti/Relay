package com.techindna.relay.entity.email;

public record AccountDeletionEmail(
        String firstName,
        String ipAddress,
        String userAgent,
        String time) {}
