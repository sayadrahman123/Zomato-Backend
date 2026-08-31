package com.zomato.backend.entity;

import com.zomato.backend.entity.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records a delivery assignment — the link between an Order and
 * the DeliveryPartner who carries it.
 *
 * Design decisions:
 *
 * 1. One Delivery per Order (OneToOne from Delivery side):
 *    FK lives on this table (order_id UNIQUE). If re-assignment is ever
 *    needed (partner cancelled mid-delivery), the old Delivery record is
 *    marked FAILED and a new Delivery row is created for the same order.
 *    This preserves full assignment history.
 *
 * 2. Unidirectional from Delivery → Order:
 *    Order entity does NOT have a Delivery collection/field.
 *    DeliveryRepository.findByOrderId() is used to fetch the assignment.
 *    This avoids coupling Order ↔ Delivery bidirectionally.
 *
 * 3. Timestamp columns (assignedAt, pickedUpAt, deliveredAt):
 *    Separate from BaseEntity's createdAt/updatedAt — each transition
 *    records the exact business event time (not the DB write time).
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "deliveries",
    indexes = {
        @Index(name = "idx_delivery_order",   columnList = "order_id",   unique = true),
        @Index(name = "idx_delivery_partner", columnList = "partner_id"),
        @Index(name = "idx_delivery_status",  columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relationships ──────────────────────────────────────────────────────────

    /**
     * The order this delivery is for. Unique — one delivery per order.
     * FK lives here (on the "many" side of the business relationship,
     * even though it's logically 1:1).
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_delivery_order")
    )
    private Order order;

    /**
     * The delivery partner assigned to this delivery.
     * ManyToOne — a partner handles many deliveries over their lifetime.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "partner_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_delivery_partner")
    )
    private DeliveryPartner partner;

    // ── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.ASSIGNED;

    // ── Business Event Timestamps ─────────────────────────────────────────────

    /**
     * When this assignment was created (partner assigned to order).
     * Set at insert time — does not change on updates.
     */
    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    /**
     * When the partner scanned/confirmed pickup at the restaurant.
     * Null until status transitions to PICKED_UP.
     */
    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    /**
     * When the partner confirmed delivery to the customer.
     * Null until status transitions to DELIVERED.
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ── Partner location at key events ────────────────────────────────────────

    /**
     * Partner's GPS coordinates at the moment of pickup.
     * Provides a sanity check that the partner was at the restaurant.
     */
    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    // ── Notes ─────────────────────────────────────────────────────────────────

    /**
     * Free-text note from the partner — e.g. "Customer not reachable, left at door".
     * Stored on FAILED deliveries for customer support investigation.
     */
    @Column(name = "partner_notes", length = 500)
    private String partnerNotes;
}
