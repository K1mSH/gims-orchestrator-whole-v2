package com.gims.auth.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
