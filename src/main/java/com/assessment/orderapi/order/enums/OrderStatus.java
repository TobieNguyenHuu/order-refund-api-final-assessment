package com.assessment.orderapi.order.enums;

import java.util.Set;

public enum OrderStatus {

    PENDING,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    CANCELLED;

    private static final Set<OrderStatus> FROM_PENDING = Set.of(CONFIRMED, CANCELLED);
    private static final Set<OrderStatus> FROM_CONFIRMED = Set.of(PROCESSING, CANCELLED);
    private static final Set<OrderStatus> FROM_PROCESSING = Set.of(COMPLETED);

    /**
     * The order state machine. COMPLETED and CANCELLED are terminal.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case CONFIRMED -> FROM_CONFIRMED.contains(target);
            case PROCESSING -> FROM_PROCESSING.contains(target);
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Spec 7.4 restricts the user-facing cancel endpoint to PENDING orders.
     * This is narrower than the state machine in 6.1, which also permits
     * CONFIRMED -> CANCELLED; that broader transition is reachable only
     * through the admin status-update endpoint.
     */
    public boolean isUserCancellable() {
        return this == PENDING;
    }
}
