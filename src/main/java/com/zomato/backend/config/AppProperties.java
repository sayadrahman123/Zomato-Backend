package com.zomato.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds all properties under the "app" prefix in application.yml
 * into a strongly-typed bean.
 *
 * This is preferred over scattering @Value("${app.jwt.secret}") across
 * multiple classes — one place to look, refactor-safe, IDE-autocomplete friendly.
 *
 * Example (application.yml):
 * <pre>
 * app:
 *   jwt:
 *     secret: your-secret-key
 *     expiration-ms: 86400000
 *   cache:
 *     restaurant-ttl-minutes: 10
 * </pre>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs;
    }

    @Getter
    @Setter
    public static class Cache {
        private long restaurantTtlMinutes;
        private long menuTtlMinutes;
        private long reviewsTtlMinutes;
        private long cartTtlDays;
    }
}
