package com.infolink.auth.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
