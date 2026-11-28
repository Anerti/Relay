package com.techindna.relay.entity.email;

public record EmailDetails(
        EmailTemplate template,
        String recipient,
        String subject,
        Record model) {}
