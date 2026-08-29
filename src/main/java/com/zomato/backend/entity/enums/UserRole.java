package com.zomato.backend.entity.enums;

/**
 * Defines the roles a user can hold in the Zomato platform.
 *
 * <ul>
 *   <li>CUSTOMER        — browses restaurants, places orders, leaves reviews</li>
 *   <li>RESTAURANT_OWNER — manages their own restaurant(s) and menu items</li>
 *   <li>DELIVERY_PARTNER — picks up and delivers orders</li>
 *   <li>ADMIN            — platform-level management (approve restaurants, ban users)</li>
 * </ul>
 */
public enum UserRole {
    CUSTOMER,
    RESTAURANT_OWNER,
    DELIVERY_PARTNER,
    ADMIN
}
