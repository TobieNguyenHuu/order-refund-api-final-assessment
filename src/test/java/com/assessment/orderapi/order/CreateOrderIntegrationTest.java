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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    private CreateOrderRequest request(List<OrderItemRequest> items) {
        return new CreateOrderRequest(items, "123 Nguyen Hue, Quy Nhon", "Test note");
    }

    @Test
    @DisplayName("Spec 11.1: creates an order and reduces stock")
    void createsOrderAndReducesStock() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        Product keyboard = createProduct("Keyboard", "1200000.00", 5, true);

        OrderResponse response = orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(mouse.getId(), 2),
                new OrderItemRequest(keyboard.getId(), 1))));

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.totalAmount()).isEqualByComparingTo("1700000.00");
        assertThat(response.orderCode()).startsWith("OFL-");
        assertThat(response.items()).hasSize(2);

        assertThat(productRepository.findById(mouse.getId()).orElseThrow().getStock())
                .isEqualTo(8);
        assertThat(productRepository.findById(keyboard.getId()).orElseThrow().getStock())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Spec 11.3: rejects duplicate product ids")
    void rejectsDuplicateProductIds() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);

        assertThatThrownBy(() -> orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(mouse.getId(), 1),
                new OrderItemRequest(mouse.getId(), 3)))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PRODUCT);

        assertThat(productRepository.findById(mouse.getId()).orElseThrow().getStock())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Spec 11.4: rejects a missing product")
    void rejectsMissingProduct() {
        assertThatThrownBy(() -> orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(999_999L, 1)))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("Spec 11.4: rejects an inactive product")
    void rejectsInactiveProduct() {
        Product headset = createProduct("Headset", "500000.00", 20, false);

        assertThatThrownBy(() -> orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(headset.getId(), 1)))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_ACTIVE);

        assertThat(productRepository.findById(headset.getId()).orElseThrow().getStock())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("Spec 11.5: rejects insufficient stock")
    void rejectsInsufficientStock() {
        Product keyboard = createProduct("Keyboard", "1200000.00", 5, true);

        assertThatThrownBy(() -> orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(keyboard.getId(), 6)))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        assertThat(productRepository.findById(keyboard.getId()).orElseThrow().getStock())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Spec 11.6: rolls back every stock change when one item fails")
    void rollsBackAllStockChangesWhenOneItemFails() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);
        Product keyboard = createProduct("Keyboard", "1200000.00", 5, true);

        // The valid item is listed first, so a naive implementation that
        // deducted as it validated would already have reduced the mouse stock
        // by the time it reached the failing item.
        assertThatThrownBy(() -> orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(mouse.getId(), 1),
                new OrderItemRequest(keyboard.getId(), 999)))))
                .isInstanceOf(AppException.class);

        assertThat(productRepository.findById(mouse.getId()).orElseThrow().getStock())
                .isEqualTo(10);
        assertThat(productRepository.findById(keyboard.getId()).orElseThrow().getStock())
                .isEqualTo(5);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Spec 11.7: uses the database price, and snapshots it on the item")
    void usesDatabasePriceAndSnapshotsIt() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);

        OrderResponse response = orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(mouse.getId(), 2))));

        assertThat(response.items().getFirst().unitPrice())
                .isEqualByComparingTo("250000.00");
        assertThat(response.items().getFirst().productName()).isEqualTo("Mouse");
        assertThat(response.totalAmount()).isEqualByComparingTo("500000.00");

        // Changing the product afterwards must not rewrite the order's history.
        mouse.setPrice(new BigDecimal("999000.00"));
        mouse.setName("Renamed Mouse");
        productRepository.save(mouse);

        OrderResponse reloaded = orderService.getMyOrderDetail(alphaUserId, response.id());
        assertThat(reloaded.items().getFirst().unitPrice())
                .isEqualByComparingTo("250000.00");
        assertThat(reloaded.items().getFirst().productName()).isEqualTo("Mouse");
    }

    @Test
    @DisplayName("Spec 11.8: rejects access to another user's order")
    void rejectsAccessToAnotherUsersOrder() {
        Product mouse = createProduct("Mouse", "250000.00", 10, true);

        OrderResponse alphaOrder = orderService.createOrder(alphaUserId, request(List.of(
                new OrderItemRequest(mouse.getId(), 1))));

        assertThatThrownBy(() -> orderService.getMyOrderDetail(betaUserId, alphaOrder.id()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }
}
