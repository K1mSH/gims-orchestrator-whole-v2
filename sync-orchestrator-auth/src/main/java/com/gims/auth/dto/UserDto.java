package com.gims.auth.dto;

import com.gims.auth.entity.AuthUser;

import java.time.LocalDateTime;

/**
 * 사용자 정보 응답 DTO.
 * password_hash / role / fail_count / locked_until 등 민감/내부 메타 제외.
 */
public record UserDto(
    Long id,
    String authUsersId,
    String name,
    LocalDateTime createdAt
) {
    public static UserDto from(AuthUser u) {
        return new UserDto(u.getId(), u.getAuthUsersId(), u.getName(), u.getCreatedAt());
    }
}
