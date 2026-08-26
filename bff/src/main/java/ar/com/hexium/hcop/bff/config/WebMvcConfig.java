package ar.com.hexium.hcop.bff.config;

import ar.com.hexium.hcop.bff.security.CurrentSessionArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentSessionArgumentResolver currentSessionArgumentResolver;

    public WebMvcConfig(CurrentSessionArgumentResolver currentSessionArgumentResolver) {
        this.currentSessionArgumentResolver = currentSessionArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentSessionArgumentResolver);
    }
}
