package com.assessment.orderapi.common.util;

import com.assessment.orderapi.common.enums.ErrorCode;
import com.assessment.orderapi.common.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthUtils {

    private AuthUtils() {
    }

    /**
     * Returns the authenticated user's id, taken from the JWT.
     * The client never supplies a user id; it is always derived from the token.
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return Long.valueOf(userId);
    }
}
