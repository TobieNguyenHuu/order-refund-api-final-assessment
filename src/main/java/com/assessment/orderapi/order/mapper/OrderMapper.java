package com.assessment.orderapi.order.mapper;

import com.assessment.orderapi.order.dto.response.OrderItemResponse;
import com.assessment.orderapi.order.dto.response.OrderResponse;
import com.assessment.orderapi.order.dto.response.OrderSummaryResponse;
import com.assessment.orderapi.order.entity.Order;
import com.assessment.orderapi.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .note(order.getNote())
                .cancelledAt(order.getCancelledAt())
                .refundedAt(order.getRefundedAt())
                .createdAt(order.getCreatedAt())
                .items(toItemResponses(order.getItems()))
                .build();
    }

    public OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        return items.stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();
    }}
