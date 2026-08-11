package com.assessment.orderapi.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    VALIDATION_ERROR("Request validation failed", HttpStatus.BAD_REQUEST),
    EMPTY_ORDER_ITEMS("Order item list is empty", HttpStatus.BAD_REQUEST),
    DUPLICATE_PRODUCT("Product ID appears more than once", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY("Quantity is invalid", HttpStatus.BAD_REQUEST),

    PRODUCT_NOT_FOUND("Product does not exist", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND("Order is missing or inaccessible", HttpStatus.NOT_FOUND),

    PRODUCT_NOT_ACTIVE("Product is inactive", HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK("Product stock is insufficient", HttpStatus.CONFLICT),
    ORDER_CANNOT_BE_CANCELLED("Current order cannot be cancelled", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION("Order status transition is invalid", HttpStatus.CONFLICT),
    PAYMENT_ALREADY_COMPLETED("Order has already been paid", HttpStatus.CONFLICT),
    ORDER_CANNOT_BE_PAID("Current order cannot be paid", HttpStatus.CONFLICT),
    REFUND_ALREADY_COMPLETED("Refund has already been completed", HttpStatus.CONFLICT),

    ACCESS_DENIED("User does not have permission", HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND("Requested resource does not exist", HttpStatus.NOT_FOUND),
    MALFORMED_REQUEST("Request body is malformed or unreadable", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("HTTP method is not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),

    UNAUTHENTICATED("Authentication required", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String defaultMessage, HttpStatus httpStatus) {
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
