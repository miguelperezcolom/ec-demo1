package io.mateu.ecdemo1.operamock.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ApiConfig implements WebMvcConfigurer {

    final OhipGuard guard;

    /**
     * Every API the double serves is behind the guard — the Property ones and the enterprise one,
     * which names the hub instead of a hotel; the token endpoint is not.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/rsv/**", "/crm/**", "/csh/**", "/rm/**", "/rtp/**", "/lov/**",
                "/ent/**");
    }

    @RestControllerAdvice(basePackageClasses = ApiConfig.class)
    static class Errors {
        @ExceptionHandler(OperaError.class)
        ResponseEntity<Map<String, Object>> opera(OperaError e) {
            return ResponseEntity.status(e.status()).body(e.body());
        }
    }
}
