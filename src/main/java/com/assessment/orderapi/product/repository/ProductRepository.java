package com.assessment.orderapi.product.repository;

import com.assessment.orderapi.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Loads products with a row-level write lock (SELECT ... FOR UPDATE).
     * ORDER BY p.id is essential: every transaction acquires locks in the same
     * order, so two concurrent orders touching the same products can never hold
     * one lock each and wait for the other. That is the deadlock prevention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdsForUpdate(@Param("ids") Collection<Long> ids);
}
