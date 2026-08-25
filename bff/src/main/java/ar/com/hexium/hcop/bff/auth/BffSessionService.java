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
    private static final String REFRESH_LOCK_PREFIX = "bff:session-refresh-lock:";
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(5);

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

    /**
     * SETNX con TTL corto: si 10 requests concurrentes de la misma sesión cruzan el umbral de
     * refresh a la vez, solo uno gana la carrera y hace el {@link #refresh}; el resto sigue de
     * largo con la sesión que ya tenía (no bloquea, no reintenta). El lock expira solo, nunca se
     * borra explícito — borrarlo abriría una ventana para que un segundo request lo tome de
     * nuevo antes de que el primero haya terminado de escribir en Redis.
     */
    public boolean tryAcquireRefreshLock(String sessionId) {
        Boolean acquired = redis.opsForValue().setIfAbsent(REFRESH_LOCK_PREFIX + sessionId, "1", REFRESH_LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /** Re-emite la sesión con un TTL nuevo. Llamar solo tras ganar {@link #tryAcquireRefreshLock}. */
    public void refresh(String sessionId, Duration newTtl) {
        find(sessionId).ifPresent(session -> store(sessionId, new BffSession(session.backendToken(), Instant.now().plus(newTtl))));
    }

    private void store(String sessionId, BffSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) ttl = Duration.ofSeconds(1);
        redis.opsForValue().set(KEY_PREFIX + sessionId, mapper.writeValueAsString(session), ttl);
    }
}
