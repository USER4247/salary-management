package com.acme.salary.auth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebMvcConfig implements WebMvcConfigurer {

    private final GuardInterceptor guardInterceptor;

    WebMvcConfig(GuardInterceptor guardInterceptor) {
        this.guardInterceptor = guardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guardInterceptor).addPathPatterns("/api/**");
    }
}
