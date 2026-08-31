package com.zomato.backend.entity.enums;

/**
 * Dietary classification for a menu item.
 *
 * Stored as VARCHAR (EnumType.STRING) — safe against reordering.
 *
 * The green/brown dot indicators commonly seen on Indian menus:
 *  VEG     → green dot (pure vegetarian)
 *  NON_VEG → brown/red dot
 *  EGG     → yellow dot (contains egg, no meat)
 *  VEGAN   → no dairy, no egg, no meat
 */
public enum FoodType {
    VEG,
    NON_VEG,
    EGG,
    VEGAN
}
