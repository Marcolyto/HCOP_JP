package ar.com.hexium.hcop.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SetCookieParserTest {

    @Test
    void extraeValorYMaxAgeDeUnaCookieRealDelBackend() {
        String header = "HCOP_SESSION=abc123XYZ_-; Path=/; Max-Age=2592000; HttpOnly; SameSite=Strict";

        Optional<SetCookieParser.ParsedCookie> parsed = SetCookieParser.parse(header, "HCOP_SESSION");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().value()).isEqualTo("abc123XYZ_-");
        assertThat(parsed.get().maxAge()).isEqualTo(Duration.ofSeconds(2592000));
    }

    @Test
    void ignoraSameSiteYOtrosAtributosNoReconocidos() {
        String header = "HCOP_SESSION=tok; Path=/; Secure; SameSite=Strict; HttpOnly";

        Optional<SetCookieParser.ParsedCookie> parsed = SetCookieParser.parse(header, "HCOP_SESSION");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().value()).isEqualTo("tok");
        assertThat(parsed.get().maxAge()).isNull();
    }

    @Test
    void devuelveVacioSiElNombreDeCookieNoCoincide() {
        String header = "OTRA_COOKIE=tok; Path=/";

        assertThat(SetCookieParser.parse(header, "HCOP_SESSION")).isEmpty();
    }

    @Test
    void devuelveVacioConHeaderNuloOVacio() {
        assertThat(SetCookieParser.parse(null, "HCOP_SESSION")).isEmpty();
        assertThat(SetCookieParser.parse("", "HCOP_SESSION")).isEmpty();
    }

    @Test
    void ignoraMaxAgeIlegibleYSiguePropagandoElValor() {
        String header = "HCOP_SESSION=tok; Max-Age=no-numerico";

        Optional<SetCookieParser.ParsedCookie> parsed = SetCookieParser.parse(header, "HCOP_SESSION");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().value()).isEqualTo("tok");
        assertThat(parsed.get().maxAge()).isNull();
    }
}
