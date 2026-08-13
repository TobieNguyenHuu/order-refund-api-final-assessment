package com.assessment.orderapi.order;

import com.assessment.orderapi.AbstractIntegrationTest;
import com.assessment.orderapi.order.dto.request.CreateOrderRequest;
import com.assessment.orderapi.order.dto.request.OrderItemRequest;
import com.assessment.orderapi.order.service.OrderService;
import com.assessment.orderapi.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bonus scenario from spec section 11: ten concurrent single-unit orders
 * against a product with five in stock. This is the test that actually proves
 * PESSIMISTIC_WRITE works — without the lock, several threads would read the
 * same stock value and oversell.
 */
class ConcurrentOrderIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 10;
    private static final int INITIAL_STOCK = 5;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("Bonus: 10 concurrent orders against stock of 5 oversell nothing")
    void concurrentOrdersNeverOversell() throws InterruptedException {
        Product limited = createProduct("Limited Keyboard", "1200000.00", INITIAL_STOCK, true);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(THREADS);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    // Release every thread at once so the requests genuinely overlap.
                    startGate.await();
                    orderService.createOrder(alphaUserId, new CreateOrderRequest(
                            List.of(new OrderItemRequest(limited.getId(), 1)),
                            "123 Nguyen Hue, Quy Nhon",
                            null));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = finishGate.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successes.get()).isEqualTo(INITIAL_STOCK);
        assertThat(failures.get()).isEqualTo(THREADS - INITIAL_STOCK);

        int finalStock = productRepository.findById(limited.getId()).orElseThrow().getStock();
        assertThat(finalStock).isZero();
        assertThat(finalStock).isNotNegative();
        assertThat(orderRepository.count()).isEqualTo(INITIAL_STOCK);
    }
}
