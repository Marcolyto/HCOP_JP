package ar.com.hexium.hcop.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class BffSessionServiceLockTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final BffSessionService sessions = new BffSessionService(redis, mapper);

    BffSessionServiceLockTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void tryAcquireRefreshLockUsaSetIfAbsentConTtlCorto() {
        when(values.setIfAbsent(eq("bff:session-refresh-lock:abc"), eq("1"), eq(Duration.ofSeconds(5))))
                .thenReturn(true);

        assertThat(sessions.tryAcquireRefreshLock("abc")).isTrue();
    }

    @Test
    void tryAcquireRefreshLockDevuelveFalseSiOtroRequestYaLoTiene() {
        when(values.setIfAbsent(eq("bff:session-refresh-lock:abc"), eq("1"), eq(Duration.ofSeconds(5))))
                .thenReturn(false);

        assertThat(sessions.tryAcquireRefreshLock("abc")).isFalse();
    }

    @Test
    void refreshReescribeLaSesionConNuevoTtlPreservandoElToken() {
        String json = mapper.writeValueAsString(new BffSession("tok-original", Instant.now().plusSeconds(60)));
        when(values.get("bff:session:abc")).thenReturn(json);

        sessions.refresh("abc", Duration.ofMinutes(43_200));

        verify(values).set(eq("bff:session:abc"), org.mockito.ArgumentMatchers.contains("tok-original"),
                org.mockito.ArgumentMatchers.<Duration>argThat(ttl -> ttl.compareTo(Duration.ofDays(29)) > 0));
    }

    @Test
    void refreshDeUnaSesionInexistenteNoHaceNada() {
        when(values.get("bff:session:missing")).thenReturn(null);

        sessions.refresh("missing", Duration.ofMinutes(43_200));

        verify(values, org.mockito.Mockito.never()).set(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
