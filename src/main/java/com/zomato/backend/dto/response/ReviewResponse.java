package com.zomato.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Public review response — shown on the restaurant detail page.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponse(
        Long          id,
        Long          customerId,
        String        customerName,
        Long          restaurantId,
        Long          orderId,
        Integer       rating,
        String        title,
        String        comment,
        Boolean       isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
