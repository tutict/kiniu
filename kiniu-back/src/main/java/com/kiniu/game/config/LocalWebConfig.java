package com.kiniu.game.config;

import com.kiniu.game.security.LocalAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(LocalAccessProperties.class)
public class LocalWebConfig implements WebMvcConfigurer {

    private final LocalAccessProperties properties;

    public LocalWebConfig(LocalAccessProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-API-Key", "X-Provider-Url", "X-Model", "X-Local-Token")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String origin = request.getHeader("Origin");
                if (origin == null || origin.isBlank() || properties.getAllowedOrigins().contains(origin.trim())) {
                    return true;
                }
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed.");
                return false;
            }
        });
    }
}