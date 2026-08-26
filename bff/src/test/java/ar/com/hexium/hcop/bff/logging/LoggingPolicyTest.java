package ar.com.hexium.hcop.bff.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingPolicyTest {

    @Test
    void excluyeElHealthcheckDelPropioContenedor() {
        assertThat(LoggingPolicy.shouldLog("/actuator/health")).isFalse();
    }

    @Test
    void loguearElRestoDeLosPaths() {
        assertThat(LoggingPolicy.shouldLog("/api/clinical/status")).isTrue();
        assertThat(LoggingPolicy.shouldLog("/api/auth/login")).isTrue();
    }
}
