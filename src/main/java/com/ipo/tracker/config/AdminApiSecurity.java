package com.ipo.tracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class AdminApiSecurity implements WebMvcConfigurer {
    @Value("${ipo.admin-token:}")
    private String adminToken;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
                String supplied = request.getHeader("X-Admin-Token");
                if (StringUtils.hasText(adminToken) && StringUtils.hasText(supplied)
                        && MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) return true;
                response.setStatus(StringUtils.hasText(adminToken) ? 401 : 503);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Administrative API is unavailable or unauthorized\"}");
                return false;
            }
        }).addPathPatterns("/api/v1/alerts/**");
    }
}
