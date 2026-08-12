package com.assessment.orderapi.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "Product name must not be blank")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.0", message = "Price must not be negative")
        BigDecimal price,

        @NotNull(message = "Stock must not be null")
        @Min(value = 0, message = "Stock must not be negative")
        Integer stock,

        Boolean active) {
}
