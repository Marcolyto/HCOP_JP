package ar.com.hexium.hcop.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.auth.BffSession;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;

class CurrentSessionArgumentResolverTest {

    private final CurrentSessionArgumentResolver resolver = new CurrentSessionArgumentResolver();

    @Test
    void soportaBffSessionDirectoYOptionalDeBffSession() throws Exception {
        assertThat(resolver.supportsParameter(param("directo", 0))).isTrue();
        assertThat(resolver.supportsParameter(param("opcional", 0))).isTrue();
        assertThat(resolver.supportsParameter(param("otroTipo", 0))).isFalse();
    }

    @Test
    void resuelveBffSessionDesdeElAtributoDelRequest() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        BffSession session = new BffSession("tok", Instant.now().plus(Duration.ofDays(1)));
        servletRequest.setAttribute(BffSessionFilter.SESSION_ATTRIBUTE, session);
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest()).thenReturn(servletRequest);

        Object direct = resolver.resolveArgument(param("directo", 0), null, webRequest, null);
        Object optional = resolver.resolveArgument(param("opcional", 0), null, webRequest, null);

        assertThat(direct).isEqualTo(session);
        assertThat(optional).isEqualTo(Optional.of(session));
    }

    @Test
    void resuelveOptionalVacioSinAtributoEnElRequest() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest()).thenReturn(servletRequest);

        Object optional = resolver.resolveArgument(param("opcional", 0), null, webRequest, null);

        assertThat(optional).isEqualTo(Optional.empty());
    }

    private MethodParameter param(String methodName, int index) throws NoSuchMethodException {
        Method method = Signatures.class.getDeclaredMethod(methodName,
                methodName.equals("directo") ? BffSession.class
                        : methodName.equals("opcional") ? Optional.class : String.class);
        return new MethodParameter(method, index);
    }

    @SuppressWarnings("unused")
    private static final class Signatures {
        void directo(BffSession session) {}

        void opcional(Optional<BffSession> session) {}

        void otroTipo(String value) {}
    }
}
