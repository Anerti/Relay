package com.techindna.relay.dto;

public record ChangePasswordInput(String oldPassword, String newPassword, String confirmNewPassword) {}
