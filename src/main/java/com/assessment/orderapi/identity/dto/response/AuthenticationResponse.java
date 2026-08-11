package com.assessment.orderapi.identity.dto.response;

import lombok.Builder;

import java.util.Set;

@Builder
public record AuthenticationResponse(
        String token,
        Long userId,
        String username,
        Set<String> roles,
        long expiresIn) {
}
