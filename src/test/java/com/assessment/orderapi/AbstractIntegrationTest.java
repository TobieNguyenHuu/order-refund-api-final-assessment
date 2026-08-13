package com.assessment.orderapi;

import com.assessment.orderapi.identity.entity.User;
import com.assessment.orderapi.identity.repository.UserRepository;
import com.assessment.orderapi.order.repository.OrderRepository;
import com.assessment.orderapi.product.entity.Product;
import com.assessment.orderapi.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

/**
 * Shared base for integration tests. A single Postgres container is reused by
 * every test class: pessimistic locking and transaction rollback only exist at
 * the database level, so mocking the repositories would let the tests pass
 * whether or not the locking works.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    protected Long alphaUserId;
    protected Long betaUserId;

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
        productRepository.deleteAll();

        alphaUserId = findUserId("useralpha");
        betaUserId = findUserId("userbeta");
    }

    private Long findUserId(String username) {
        return userRepository.findByUsernameOrEmail(username)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed user %s is missing".formatted(username)));
    }

    protected Product createProduct(String name, String price, int stock, boolean active) {
        return productRepository.save(Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .active(active)
                .build());
    }
}
