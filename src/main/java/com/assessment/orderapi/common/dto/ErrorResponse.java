package com.assessment.orderapi.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private List<FieldErrorItem> fieldErrors;

    @Getter
    @Builder
    public static class FieldErrorItem {
        private String field;
        private String message;
    }
}
