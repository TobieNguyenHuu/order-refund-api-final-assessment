package com.assessment.orderapi.order;

import com.assessment.orderapi.AbstractIntegrationTest;
import com.assessment.orderapi.common.enums.ErrorCode;
import com.assessment.orderapi.common.exception.AppException;
import com.assessment.orderapi.order.dto.request.CreateOrderRequest;
import com.assessment.orderapi.order.dto.request.OrderItemRequest;
import com.assessment.orderapi.order.dto.response.OrderResponse;
import com.assessment.orderapi.order.enums.OrderStatus;
import com.assessment.orderapi.order.enums.PaymentStatus;
import com.assessment.orderapi.order.service.OrderService;
import com.assessment.orderapi.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    private OrderResponse createOrderFor(Long userId, Product product, int quantity) {
        return orderService.createOrder(userId, new CreateOrderRequest(
                List.of(new OrderItemRequest(product.getId(), quantity)),
                "123 Nguyen Hue, Quy Nhon",
                null));
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    @Test
    @DisplayName("Spec 11.9: cancels a pending unpaid order and restores stock")
    void cancelsUnpaidOrderAndRestoresStock() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 3);
        assertThat(stockOf(mouse)).isEqualTo(7);

        OrderResponse cancelled = orderService.cancelOrder(alphaUserId, order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(cancelled.refundedAt()).isNull();
        assertThat(stockOf(mouse)).isEqualTo(10);
    }

    @Test
    @DisplayName("Spec 11.10: cancelling a paid order restores stock and sets REFUNDED")
    void cancelsPaidOrderAndRefunds() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 2);

        OrderResponse paid = orderService.payOrder(alphaUserId, order.id());
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(paid.status()).isEqualTo(OrderStatus.PENDING.name());

        OrderResponse cancelled = orderService.cancelOrder(alphaUserId, order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        assertThat(cancelled.refundedAt()).isNotNull();
        assertThat(stockOf(mouse)).isEqualTo(10);
    }

    @Test
    @DisplayName("Spec 11.11: repeated cancellation is rejected and does not restore stock again")
    void rejectsRepeatedCancellation() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 4);

        orderService.cancelOrder(alphaUserId, order.id());
        assertThat(stockOf(mouse)).isEqualTo(10);

        assertThatThrownBy(() -> orderService.cancelOrder(alphaUserId, order.id()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CANNOT_BE_CANCELLED);

        assertThat(stockOf(mouse)).isEqualTo(10);
    }

    @Test
    @DisplayName("Repeated payment is rejected")
    void rejectsRepeatedPayment() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 1);

        orderService.payOrder(alphaUserId, order.id());

        assertThatThrownBy(() -> orderService.payOrder(alphaUserId, order.id()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("Spec 11.12: rejects an invalid status transition")
    void rejectsInvalidStatusTransition() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 1);

        orderService.updateOrderStatus(order.id(), OrderStatus.CONFIRMED);

        // CONFIRMED may only move to PROCESSING or CANCELLED.
        assertThatThrownBy(() ->
                orderService.updateOrderStatus(order.id(), OrderStatus.COMPLETED))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("Admin cancellation restores stock and refunds, like a user cancellation")
    void adminCancellationRestoresStockAndRefunds() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 3);
        orderService.payOrder(alphaUserId, order.id());

        OrderResponse cancelled =
                orderService.updateOrderStatus(order.id(), OrderStatus.CANCELLED);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        assertThat(stockOf(mouse)).isEqualTo(10);
    }

    @Test
    @DisplayName("A user cannot cancel a CONFIRMED order (spec 7.4 is PENDING only)")
    void userCannotCancelConfirmedOrder() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        OrderResponse order = createOrderFor(alphaUserId, mouse, 1);

        orderService.updateOrderStatus(order.id(), OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> orderService.cancelOrder(alphaUserId, order.id()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
    }
}
