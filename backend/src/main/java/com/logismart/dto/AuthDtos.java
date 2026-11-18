package com.logismart.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, String fullName, String role, Instant expiresAt) {}

    public record MeResponse(String username, String fullName, String role) {}
}
