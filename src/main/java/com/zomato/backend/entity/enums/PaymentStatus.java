package com.zomato.backend.entity.enums;

/**
 * Payment state for an order.
 *
 *  PENDING  : Payment not yet attempted or initiated.
 *  PAID     : Payment successful (Razorpay confirmed). Terminal.
 *  FAILED   : Payment attempt failed. Order can be retried.
 *  REFUNDED : Amount returned to customer (e.g., after cancellation). Terminal.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}
