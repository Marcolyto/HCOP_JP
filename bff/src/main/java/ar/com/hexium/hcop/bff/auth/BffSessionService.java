package ar.com.hexium.hcop.bff.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Único componente del BFF que conoce Redis. Guarda cada {@link BffSession} bajo
 * {@code bff:session:<uuid>} con TTL igual al tiempo que le queda a la sesión del backend —
 * si Redis reinicia (sin persistencia, ver compose F1.5) todos quedan deslogueados, es
 * comportamiento esperado.
 */
@Service
public class BffSessionService {

    private static final String KEY_PREFIX = "bff:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public BffSessionService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public String create(BffSession session) {
        String sessionId = UUID.randomUUID().toString();
        store(sessionId, session);
        return sessionId;
    }

    public Optional<BffSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        String raw = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(raw, BffSession.class));
        } catch (RuntimeException malformed) {
            // Sesión corrupta en Redis (ej. cambio de shape entre despliegues): tratarla como
            // ausente en vez de romper el request, es lo que ya pasa con una sesión vencida.
            return Optional.empty();
        }
    }

    public void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        redis.delete(KEY_PREFIX + sessionId);
    }

    private void store(String sessionId, BffSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) ttl = Duration.ofSeconds(1);
        redis.opsForValue().set(KEY_PREFIX + sessionId, mapper.writeValueAsString(session), ttl);
    }
}
