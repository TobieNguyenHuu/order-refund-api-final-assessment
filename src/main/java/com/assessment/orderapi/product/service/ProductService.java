package com.assessment.orderapi.product.service;

import com.assessment.orderapi.common.enums.ErrorCode;
import com.assessment.orderapi.common.exception.AppException;
import com.assessment.orderapi.product.dto.request.CreateProductRequest;
import com.assessment.orderapi.product.dto.response.ProductResponse;
import com.assessment.orderapi.product.entity.Product;
import com.assessment.orderapi.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .active(request.active() == null || request.active())
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product %d does not exist".formatted(productId)));
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .active(product.isActive())
                .build();
    }
}
