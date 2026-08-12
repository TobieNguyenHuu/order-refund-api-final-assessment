package com.assessment.orderapi.order.repository;

import com.assessment.orderapi.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Ownership is part of the query, not an afterwards check. A miss is
     * indistinguishable from "does not exist", which is what lets the service
     * return 404 rather than revealing that the order belongs to someone else.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("orderId") Long orderId,
                                               @Param("userId") Long userId);

    @Query(value = "SELECT nextval('order_code_seq')", nativeQuery = true)
    Long nextOrderCodeSequence();
}
