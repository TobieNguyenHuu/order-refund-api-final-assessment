package com.assessment.orderapi.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "Order item list must not be empty")
        @Valid
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address must not be blank")
        @Size(max = 500, message = "Shipping address must not exceed 500 characters")
        String shippingAddress,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note) {
}
