package com.techindna.relay.entity.email;

import java.util.Map;

public record EmailDetails(
        EmailTemplate template,
        String recipient,
        String subject,
        Map<String, Object> variables) {}
