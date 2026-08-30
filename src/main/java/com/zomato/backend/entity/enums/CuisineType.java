package com.zomato.backend.entity.enums;

/**
 * Cuisine categories available on the platform.
 * <p>
 * Stored as VARCHAR (EnumType.STRING) so adding new types
 * never corrupts existing rows.
 */
public enum CuisineType {
    NORTH_INDIAN,
    SOUTH_INDIAN,
    CHINESE,
    ITALIAN,
    PIZZA,
    BURGER,
    BIRYANI,
    SEAFOOD,
    MUGHLAI,
    STREET_FOOD,
    DESSERTS,
    BEVERAGES,
    BAKERY,
    HEALTHY,
    FAST_FOOD,
    OTHER
}
