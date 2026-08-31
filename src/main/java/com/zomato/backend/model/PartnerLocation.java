package com.zomato.backend.model;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Snapshot of a delivery partner's last known location.
 * Stored as a Redis Hash at key "partner:location:{partnerId}".
 *
 * Why both Redis GEO and this Hash?
 *   Redis GEO (Sorted Set) is excellent for radius queries —
 *   "find all partners within 5 km of these coordinates."
 *   But GEO only stores the member name + encoded coordinates.
 *   This Hash stores the metadata we also need: exact timestamp,
 *   whether the partner is on an active delivery, etc.
 *   Both are updated atomically in LocationTrackingService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerLocation implements Serializable {

    private Long          partnerId;
    private Double        latitude;
    private Double        longitude;

    /**
     * When this location was last reported by the partner's device.
     * Used to detect stale locations (partner app crashed, battery dead, etc.).
     */
    private LocalDateTime updatedAt;

    /**
     * The order the partner is currently delivering.
     * Null if the partner is available and not on a delivery.
     * Used to show "partner is heading to pickup / en route to you".
     */
    private Long          activeOrderId;
}
