package ru.korteng.finance_manager.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF отключён осознанно: аутентификация полностью stateless (JWT в заголовке
                // Authorization, не в cookie), поэтому браузер не может автоматически подставить
                // токен в чужой запрос - классическая CSRF-атака здесь структурно невозможна.
                .csrf(csrf -> csrf.disable())
                // CORS здесь намеренно не настроен: единственная внешняя точка входа - api-gateway
                // (см. его spring.cloud.gateway.globalcors), и если добавить CORS ещё и тут, оба
                // слоя проставят Access-Control-Allow-Origin на один и тот же проксируемый ответ -
                // браузер увидит задублированное значение заголовка и отклонит его как невалидный.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
