package com.assessment.orderapi.order.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record OrderResponse(
        Long id,
        String orderCode,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        String shippingAddress,
        String note,
        LocalDateTime cancelledAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt,
        List<OrderItemResponse> items) {
}
