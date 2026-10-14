package com.kiniu.game.config;

import com.kiniu.game.security.LocalAccessGuard;
import com.kiniu.game.security.LocalAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(LocalAccessProperties.class)
public class LocalWebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(LocalWebConfig.class);

    private final LocalAccessProperties properties;
    private final LocalAccessGuard accessGuard;

    public LocalWebConfig(LocalAccessProperties properties, LocalAccessGuard accessGuard) {
        this.properties = properties;
        this.accessGuard = accessGuard;
        if (!properties.requiresToken()) {
            log.warn("No local access token is configured (game.security.local-token is empty). "
                    + "Every endpoint is reachable by any process that can open http://127.0.0.1:8080. "
                    + "Set KINIU_LOCAL_TOKEN, or start with start.ps1, unless you intend an open local API.");
        }
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
        // One interceptor enforces both the browser Origin allowlist and the local access token, so
        // every endpoint is covered and no controller can forget the check.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!originAllowed(request)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed.");
                    return false;
                }
                // CORS preflight requests never carry custom headers; the browser sends the token on
                // the real request that follows.
                if (CorsUtils.isPreFlightRequest(request)) {
                    return true;
                }
                if (!accessGuard.allows(request)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid local access token.");
                    return false;
                }
                return true;
            }
        }).excludePathPatterns("/error");
    }

    private boolean originAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        return origin == null || origin.isBlank() || properties.getAllowedOrigins().contains(origin.trim());
    }
}
