package com.techindna.relay.entity.email;

public record PasswordChangeEmail(
        String firstName,
        String time,
        ClientDetails client) {}
