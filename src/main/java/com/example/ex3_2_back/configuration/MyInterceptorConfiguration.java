package com.example.ex3_2_back.configuration;

import com.example.ex3_2_back.interceptor.AuthInterceptor;
import com.example.ex3_2_back.interceptor.RateLimitInterceptor;
import com.example.ex3_2_back.interceptor.WorkerAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyInterceptorConfiguration implements WebMvcConfigurer {
    AuthInterceptor authInterceptor;
    WorkerAuthInterceptor workerAuthInterceptor;

    @Value("${interceptor}")
    boolean enabled = false;

    @Autowired
    public void setAuthInterceptor(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    RateLimitInterceptor rateLimitInterceptor;

    @Autowired
    public void setRateLimitInterceptor(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

//    @Autowired
//    public void setWorkerAuthInterceptor(WorkerAuthInterceptor workerAuthInterceptor) {
//        this.workerAuthInterceptor = workerAuthInterceptor;
//    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry
//                .addInterceptor(authInterceptor)
//                .addPathPatterns("/**")
//                .excludePathPatterns(
//                        "/auth/**",
//                        "/dev/**",
//                        "/css/**",
//                        "/favicon.ico",
//                        "/doc.html",
//                        "/v3/api-docs",
//                        "/v3/api-docs/**",
//                        "/apiproject/swagger-ui.html",
//                        "/doc.html",
//                        "/doc.html#/**",
//                        "/webjars/**"
//                );

//        registry.addInterceptor(workerAuthInterceptor)
//                .addPathPatterns("/api/protected/**"); // Only intercept requests to "/api/protected/**"

//        registry.addInterceptor(rateLimitInterceptor)
//                .addPathPatterns("/**"); // 在此添加需要进行速率限制的请求路径
    }


}
