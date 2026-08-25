package ar.com.hexium.hcop.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class BffSessionServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final BffSessionService sessions = new BffSessionService(redis, mapper);

    BffSessionServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void creaUnaSesionYLaGuardaEnRedisConTtlHastaElVencimientoDelRefresh() {
        Instant accessExpiresAt = Instant.now().plus(Duration.ofMinutes(15));
        Instant refreshExpiresAt = Instant.now().plus(Duration.ofMinutes(30));
        BffSession session = new BffSession("access-123", accessExpiresAt, "refresh-123", refreshExpiresAt);

        String sessionId = sessions.create(session);

        assertThat(sessionId).isNotBlank();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(keyCaptor.capture(), jsonCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("bff:session:" + sessionId);
        assertThat(jsonCaptor.getValue()).contains("access-123").contains("refresh-123");
        assertThat(ttlCaptor.getValue()).isCloseTo(Duration.ofMinutes(30), Duration.ofSeconds(5));
    }

    @Test
    void unaSesionYaVencidaUsaUnTtlMinimoEnVezDeUnoNegativo() {
        BffSession session = new BffSession(
                "access-vencido", Instant.now().minusSeconds(120),
                "refresh-vencido", Instant.now().minusSeconds(60));

        sessions.create(session);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(anyString(), anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void encuentraUnaSesionExistenteYLaDeserializa() {
        Instant accessExpiresAt = Instant.now().plus(Duration.ofMinutes(15));
        Instant refreshExpiresAt = Instant.now().plus(Duration.ofMinutes(30));
        String json = mapper.writeValueAsString(
                new BffSession("access-xyz", accessExpiresAt, "refresh-xyz", refreshExpiresAt));
        when(values.get("bff:session:abc")).thenReturn(json);

        Optional<BffSession> found = sessions.find("abc");

        assertThat(found).isPresent();
        assertThat(found.get().accessToken()).isEqualTo("access-xyz");
        assertThat(found.get().refreshToken()).isEqualTo("refresh-xyz");
    }

    @Test
    void devuelveVacioSiLaSesionNoEstaEnRedis() {
        when(values.get(eq("bff:session:missing"))).thenReturn(null);

        assertThat(sessions.find("missing")).isEmpty();
    }

    @Test
    void devuelveVacioConUnSessionIdNuloOEnBlanco() {
        assertThat(sessions.find(null)).isEmpty();
        assertThat(sessions.find("  ")).isEmpty();
        verify(values, never()).get(any());
    }

    @Test
    void tolerarUnaSesionCorruptaEnRedisComoAusenteEnVezDeRomperElRequest() {
        when(values.get("bff:session:corrupta")).thenReturn("{esto-no-es-json-valido");

        assertThat(sessions.find("corrupta")).isEmpty();
    }

    @Test
    void borraLaSesionDeRedis() {
        sessions.delete("abc");

        verify(redis).delete("bff:session:abc");
    }

    @Test
    void borrarConSessionIdNuloOEnBlancoNoLlamaARedis() {
        sessions.delete(null);
        sessions.delete("");

        verify(redis, never()).delete(anyString());
    }
}
