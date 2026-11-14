package com.techindna.relay.dto;

import com.techindna.relay.entity.User;

public record VerifyRegistrationResponse(String token, User user) {}
