package com.techindna.relay.entity.email;

public enum EmailTemplate {
    REGISTRATION_VERIFICATION("mail/registration-verification");

    private final String view;

    EmailTemplate(String view) {
        this.view = view;
    }

    public String view() {
        return view;
    }
}
