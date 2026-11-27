package com.techindna.relay.entity.email;

public enum EmailTemplate {
    REGISTRATION_VERIFICATION("mail/registration-verification"),
    LOGIN_VERIFICATION("mail/login-verification"),
    ACCOUNT_DELETION_NOTIFICATION("mail/account-deletion-notification"),
    PASSWORD_CHANGE_NOTIFICATION("mail/password-change-notification");

    private final String view;

    EmailTemplate(String view) {
        this.view = view;
    }

    public String view() {
        return view;
    }
}
