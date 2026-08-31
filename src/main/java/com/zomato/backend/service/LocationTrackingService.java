package com.zomato.backend.service;

import com.zomato.backend.model.PartnerLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Real-time location tracking for delivery partners using Redis.
 *
 * Two complementary Redis structures per partner:
 *
 * 1. Redis GEO Sorted Set  — key: "geo:partners"
 *    Member: "{partnerId}"  (String)
 *    Coordinates: (longitude, latitude) encoded as a geohash score.
 *    Used for GEORADIUS queries: "find all partners within 5 km of X".
 *    ─────────────────────────────────────────────────────────────────────
 *
 * 2. Redis Hash             — key: "partner:location:{partnerId}"
 *    Fields: partnerId, latitude, longitude, updatedAt, activeOrderId
 *    TTL: 1 hour — auto-expires if the partner's app stops sending updates.
 *    Used for: metadata lookup, staleness detection, active order linkage.
 *    ─────────────────────────────────────────────────────────────────────
 *
 * Both structures are updated together on every location ping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationTrackingService {

    private static final String GEO_KEY            = "geo:partners";
    private static final String LOCATION_KEY_PREFIX = "partner:location:";
    private static final long   LOCATION_TTL_HOURS  = 1L;

    private final RedisTemplate<String, Object> redisTemplate;

    // ── Update Location ───────────────────────────────────────────────────────

    /**
     * Updates a partner's location in both the GEO set and the metadata Hash.
     * Called by the partner's app every N seconds while online.
     *
     * @param partnerId the authenticated partner's ID
     * @param latitude  current GPS latitude
     * @param longitude current GPS longitude
     */
    public void updateLocation(Long partnerId, Double latitude, Double longitude) {
        // 1. Update Redis GEO set
        GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
        geoOps.add(GEO_KEY, new Point(longitude, latitude), partnerId.toString());

        // 2. Update metadata Hash
        PartnerLocation location = PartnerLocation.builder()
                .partnerId(partnerId)
                .latitude(latitude)
                .longitude(longitude)
                .updatedAt(LocalDateTime.now())
                .build();

        String hashKey = locationKey(partnerId);
        redisTemplate.opsForValue().set(hashKey, location);
        redisTemplate.expire(hashKey, LOCATION_TTL_HOURS, TimeUnit.HOURS);

        log.debug("Location updated: partnerId={}, lat={}, lng={}", partnerId, latitude, longitude);
    }

    /**
     * Links an active order to the partner's location record.
     * Allows customers to see "your partner is on their way" context.
     *
     * @param partnerId     the partner's ID
     * @param activeOrderId the order they are currently delivering (null to clear)
     */
    public void setActiveOrder(Long partnerId, Long activeOrderId) {
        Object raw = redisTemplate.opsForValue().get(locationKey(partnerId));
        if (raw instanceof PartnerLocation location) {
            location.setActiveOrderId(activeOrderId);
            String hashKey = locationKey(partnerId);
            redisTemplate.opsForValue().set(hashKey, location);
            redisTemplate.expire(hashKey, LOCATION_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    // ── Get Location ──────────────────────────────────────────────────────────

    /**
     * Returns the last known location of a partner.
     * Empty if the TTL has expired (partner went offline > 1 hour ago).
     */
    public Optional<PartnerLocation> getPartnerLocation(Long partnerId) {
        Object raw = redisTemplate.opsForValue().get(locationKey(partnerId));
        if (raw instanceof PartnerLocation location) {
            return Optional.of(location);
        }
        return Optional.empty();
    }

    // ── Find Nearby Partners ──────────────────────────────────────────────────

    /**
     * Returns IDs of delivery partners within {@code radiusKm} km of the
     * given coordinates, using Redis GEORADIUS.
     *
     * Results are ordered by distance (nearest first), limited to {@code limit}.
     * Callers filter these IDs against the DB for isAvailable=true + isVerified=true.
     *
     * @param latitude  restaurant or customer latitude
     * @param longitude restaurant or customer longitude
     * @param radiusKm  search radius in kilometres
     * @param limit     max number of partner IDs to return
     * @return list of partnerId strings, nearest first
     */
    public List<Long> findNearbyPartnerIds(
            Double latitude, Double longitude, double radiusKm, int limit
    ) {
        GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();

        Circle within = new Circle(
                new Point(longitude, latitude),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit);

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results =
                geoOps.radius(GEO_KEY, within, args);

        List<Long> ids = new ArrayList<>();
        if (results != null) {
            results.getContent().forEach(result -> {
                Object name = result.getContent().getName();
                try {
                    ids.add(Long.parseLong(name.toString()));
                } catch (NumberFormatException ignored) {}
            });
        }
        return ids;
    }

    // ── Remove from Tracking ──────────────────────────────────────────────────

    /**
     * Removes a partner from the GEO set and deletes their location Hash.
     * Called when a partner goes offline (isAvailable = false).
     */
    public void removePartner(Long partnerId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, partnerId.toString());
        redisTemplate.delete(locationKey(partnerId));
        log.info("Partner removed from location tracking: partnerId={}", partnerId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String locationKey(Long partnerId) {
        return LOCATION_KEY_PREFIX + partnerId;
    }
}
