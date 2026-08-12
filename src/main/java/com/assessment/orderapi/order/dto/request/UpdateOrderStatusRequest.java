package com.assessment.orderapi.order.dto.request;

import com.assessment.orderapi.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(

        @NotNull(message = "Status must not be null")
        OrderStatus status) {
}
