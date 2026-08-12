package com.assessment.orderapi.product.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer stock,
        boolean active) {
}
