package com.techindna.relay.dto;

public record RegisterInput(
        String username,
        String password,
        String confirmPassword,
        String firstName,
        String lastName,
        String email) {}
