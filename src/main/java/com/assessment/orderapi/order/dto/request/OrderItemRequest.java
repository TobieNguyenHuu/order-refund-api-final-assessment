package com.assessment.orderapi.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(

        @NotNull(message = "Product id must not be null")
        Long productId,

        @NotNull(message = "Quantity must not be null")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity) {
}
