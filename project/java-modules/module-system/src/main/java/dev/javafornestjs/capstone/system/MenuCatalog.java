package dev.javafornestjs.capstone.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javafornestjs.capstone.system.AuthorizationMapper.MenuRow;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class MenuCatalog {
    private static final Duration TTL = Duration.ofMinutes(5);
    private final AuthorizationMapper menus;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;

    MenuCatalog(AuthorizationMapper menus, StringRedisTemplate redis, ObjectMapper json,
            @Value("${capstone.menu-cache.enabled}") boolean enabled) {
        this.menus = menus;
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
    }

    List<MenuRow> current() {
        if (!enabled) {
            return menus.menus();
        }
        // The caller holds a read-only MySQL transaction. Its revision and
        // definition rows therefore come from the same consistent snapshot.
        String key = "capstone:menu:" + menus.menuRevision();
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                var rows = json.readValue(cached, MenuRow[].class);
                if (rows != null && Arrays.stream(rows).allMatch(Objects::nonNull)) {
                    return List.of(rows);
                }
            }
        } catch (DataAccessException unavailable) {
            return menus.menus();
        } catch (JsonProcessingException malformed) {
            // Rebuild an invalid cached value from MySQL.
        }
        var actual = menus.menus();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(actual), TTL);
        } catch (DataAccessException | JsonProcessingException unavailable) {
            // The current database result stays usable if caching fails.
        }
        return actual;
    }
}
