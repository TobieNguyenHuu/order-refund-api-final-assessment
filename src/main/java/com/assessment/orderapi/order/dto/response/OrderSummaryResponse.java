package com.assessment.orderapi.order.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record OrderSummaryResponse(
        Long id,
        String orderCode,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {
}
