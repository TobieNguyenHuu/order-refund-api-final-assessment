package com.assessment.orderapi.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequest(

        @NotBlank(message = "Username or email must not be blank")
        String login,

        @NotBlank(message = "Password must not be blank")
        String password) {
}
