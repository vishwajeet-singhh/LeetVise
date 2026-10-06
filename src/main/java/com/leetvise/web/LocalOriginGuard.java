package com.leetvise.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.regex.Pattern;

/**
 * Rejects API calls coming from other websites open in your browser – only the LeetVise page
 * itself (localhost) may change your Excel file.
 */
@Configuration
public class LocalOriginGuard implements WebMvcConfigurer, HandlerInterceptor {

    private static final Pattern LOCAL = Pattern.compile("http://(localhost|127\\.0\\.0\\.1)(:\\d+)?");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String origin = req.getHeader("Origin");
        if (origin == null || LOCAL.matcher(origin).matches()) return true;
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Forbidden origin\"}");
        return false;
    }
}
