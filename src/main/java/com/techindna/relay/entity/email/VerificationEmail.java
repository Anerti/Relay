package com.techindna.relay.entity.email;

public record VerificationEmail(
        String firstName,
        String lastName,
        String username,
        String email,
        String time,
        String verificationUrl,
        ClientDetails client) {}
