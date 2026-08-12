package com.assessment.orderapi.order.service;

import com.assessment.orderapi.common.enums.ErrorCode;
import com.assessment.orderapi.common.exception.AppException;
import com.assessment.orderapi.identity.entity.User;
import com.assessment.orderapi.identity.repository.UserRepository;
import com.assessment.orderapi.order.dto.request.CreateOrderRequest;
import com.assessment.orderapi.order.dto.request.OrderItemRequest;
import com.assessment.orderapi.order.dto.response.OrderResponse;
import com.assessment.orderapi.order.dto.response.OrderSummaryResponse;
import com.assessment.orderapi.order.entity.Order;
import com.assessment.orderapi.order.entity.OrderItem;
import com.assessment.orderapi.order.enums.OrderStatus;
import com.assessment.orderapi.order.enums.PaymentStatus;
import com.assessment.orderapi.order.mapper.OrderMapper;
import com.assessment.orderapi.order.repository.OrderRepository;
import com.assessment.orderapi.product.entity.Product;
import com.assessment.orderapi.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_CODE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    /**
     * Creates an order.
     *
     * The whole method runs in one transaction. Every product row touched is
     * locked with PESSIMISTIC_WRITE for the duration, so a concurrent order for
     * the same products waits rather than reading stale stock. If any item
     * fails validation, nothing is written at all: no stock is deducted before
     * every item has been checked.
     */
    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {

        // Step 1: reject duplicate product ids.
        // Must happen before locking: "WHERE id IN (1,1)" returns a single row,
        // so two lines for product 1 would each be validated against the full
        // stock instead of their combined quantity.
        List<Long> productIds = extractDistinctProductIds(request.items());

        // Step 2 + 3: lock the product rows. The repository query orders by id,
        // so every transaction acquires locks in the same sequence and two
        // concurrent orders can never each hold a lock the other is waiting for.
        List<Product> lockedProducts = productRepository.findAllByIdsForUpdate(productIds);

        Map<Long, Product> productById = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Step 4 + 5: validate everything before any state changes.
        for (OrderItemRequest item : request.items()) {
            Product product = productById.get(item.productId());

            if (product == null) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product %d does not exist".formatted(item.productId()));
            }
            if (!product.isActive()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE,
                        "Product %d is not active".formatted(item.productId()));
            }
            if (product.getStock() < item.quantity()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Product %d has only %d items remaining"
                                .formatted(item.productId(), product.getStock()));
            }
        }

        // Past this point every item is known to be valid, so the writes below
        // cannot leave the order half-applied.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .totalAmount(BigDecimal.ZERO)
                .shippingAddress(request.shippingAddress())
                .note(request.note())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productById.get(itemRequest.productId());

            // Step 6: the price comes from the database row, never from the
            // request. Name and price are snapshotted so later product edits
            // do not rewrite the history of this order.
            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemRequest.quantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(subtotal);

            // Step 7: deduct stock. Safe because the row is locked and the
            // quantity was already validated above.
            product.setStock(product.getStock() - itemRequest.quantity());
        }

        order.setTotalAmount(totalAmount);
        Order saved = orderRepository.save(order);

        log.info("Order {} created by user {} with {} items, total {}",
                saved.getOrderCode(), userId, saved.getItems().size(), totalAmount);

        return orderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return orderRepository.findByUserId(userId, pageable)
                .map(orderMapper::toSummaryResponse);
    }

    /**
     * Ownership is enforced inside the query. A row belonging to someone else
     * is indistinguishable from one that does not exist, so the caller cannot
     * probe for other users' order ids.
     */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND,
                        "Order %d does not exist".formatted(orderId)));

        return orderMapper.toResponse(order);
    }

    /**
     * Cancels the caller's own order.
     *
     * Restocking is driven by the order's own state, not by cancelledAt, so a
     * second cancel attempt is rejected by the status check before any stock
     * can be returned twice.
     */
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND,
                        "Order %d does not exist".formatted(orderId)));

        if (!order.getStatus().isUserCancellable()) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED,
                    "Order in status %s cannot be cancelled".formatted(order.getStatus()));
        }

        applyCancellation(order);
        return orderMapper.toResponse(order);
    }

    /**
     * Mock payment. Only the payment status changes; the order stays in
     * whatever fulfilment status it already had, so a PENDING order can be
     * PAID. Cancellation therefore has to handle the paid case explicitly.
     */
    @Transactional
    public OrderResponse payOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND,
                        "Order %d does not exist".formatted(orderId)));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_COMPLETED,
                    "Order %s has already been paid".formatted(order.getOrderCode()));
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_PAID,
                    "Order %s has been refunded and cannot be paid".formatted(order.getOrderCode()));
        }
        // Spec 7.5: only a PENDING order with UNPAID payment status is payable.
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_PAID,
                    "Order in status %s cannot be paid".formatted(order.getStatus()));
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        log.info("Order {} marked as PAID by user {}", order.getOrderCode(), userId);

        return orderMapper.toResponse(order);
    }

    /**
     * Administrative status update. Transitions are validated against the
     * state machine in OrderStatus; a transition to CANCELLED goes through the
     * same restock-and-refund path as a user cancellation.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus targetStatus) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND,
                        "Order %d does not exist".formatted(orderId)));

        if (!order.getStatus().canTransitionTo(targetStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change status from %s to %s"
                            .formatted(order.getStatus(), targetStatus));
        }

        if (targetStatus == OrderStatus.CANCELLED) {
            applyCancellation(order);
        } else {
            order.setStatus(targetStatus);
        }

        log.info("Order {} status changed to {}", order.getOrderCode(), order.getStatus());
        return orderMapper.toResponse(order);
    }

    /**
     * Single cancellation path shared by the user and admin endpoints: restore
     * stock, mark the order cancelled, and refund it if it had been paid.
     * Product rows are re-locked here for the same reason as on creation.
     */
    private void applyCancellation(Order order) {
        List<Long> productIds = order.getItems().stream()
                .map(OrderItem::getProductId)
                .sorted()
                .toList();

        Map<Long, Product> productById = productRepository.findAllByIdsForUpdate(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (OrderItem item : order.getItems()) {
            Product product = productById.get(item.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            order.setRefundedAt(LocalDateTime.now());
            log.info("Order {} refunded on cancellation", order.getOrderCode());
        }
    }

    private List<Long> extractDistinctProductIds(List<OrderItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.productId())) {
                throw new AppException(ErrorCode.DUPLICATE_PRODUCT,
                        "Product %d appears more than once".formatted(item.productId()));
            }
        }
        return seen.stream().sorted().toList();
    }

    private String generateOrderCode() {
        Long sequence = orderRepository.nextOrderCodeSequence();
        return "OFL-%s-%06d".formatted(LocalDate.now().format(ORDER_CODE_DATE), sequence);
    }
}
