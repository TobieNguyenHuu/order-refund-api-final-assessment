package com.assessment.orderapi.order.controller;

import com.assessment.orderapi.common.util.AuthUtils;
import com.assessment.orderapi.order.dto.request.CreateOrderRequest;
import com.assessment.orderapi.order.dto.response.OrderResponse;
import com.assessment.orderapi.order.dto.response.OrderSummaryResponse;
import com.assessment.orderapi.order.dto.response.PageResponse;
import com.assessment.orderapi.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Create an order")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = AuthUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(userId, request));
    }

    @Operation(summary = "List the current user's orders")
    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = AuthUtils.getCurrentUserId();
        return ResponseEntity.ok(
                PageResponse.from(orderService.getMyOrders(userId, page, size)));
    }

    @Operation(summary = "Get the current user's order detail")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getMyOrderDetail(@PathVariable Long orderId) {
        Long userId = AuthUtils.getCurrentUserId();
        return ResponseEntity.ok(orderService.getMyOrderDetail(userId, orderId));
    }
}
