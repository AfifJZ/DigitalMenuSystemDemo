package com.example.menumanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private ManagerAuthInterceptor managerAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(managerAuthInterceptor)
                .addPathPatterns("/manage", "/manage/**", "/staff", "/staff/**",
                        "/kitchen", "/kitchen/**", "/billing", "/billing/**")
                .excludePathPatterns("/manage/login", "/manage/register", "/manage/logout",
                        "/manage/forgot-password", "/manage/forgot-password/**",
                        "/manage/reset-password", "/manage/reset-password/**");
    }
}
