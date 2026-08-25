package ar.com.hexium.hcop.bff.security;

import ar.com.hexium.hcop.bff.auth.BffSession;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Inyecta {@code Optional<BffSession>} (o {@code BffSession} directo, puede ser {@code null} en
 * un endpoint público) en la firma de un controller, leyendo lo que {@link BffSessionFilter} ya
 * dejó en el request — sin volver a pegarle a Redis.
 */
@Component
public class CurrentSessionArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (BffSession.class.equals(type)) return true;
        if (!Optional.class.equals(type)) return false;
        Type generic = parameter.getGenericParameterType();
        return generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && BffSession.class.equals(parameterized.getActualTypeArguments()[0]);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object nativeRequest = webRequest.getNativeRequest();
        BffSession session = null;
        if (nativeRequest instanceof HttpServletRequest servletRequest
                && servletRequest.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE) instanceof BffSession found) {
            session = found;
        }
        return Optional.class.equals(parameter.getParameterType()) ? Optional.ofNullable(session) : session;
    }
}
