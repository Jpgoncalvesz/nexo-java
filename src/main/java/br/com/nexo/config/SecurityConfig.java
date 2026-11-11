package br.com.nexo.config;

import br.com.nexo.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username).map(user -> User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles(user.getRole()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/openapi.yaml", "/api/csrf", "/api/health", "/api/info", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/produtos", "/api/produtos/*/reposicoes").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, auth) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> jsonError(response, 401, "CREDENCIAIS_INVALIDAS", "Usuário ou senha inválidos."))
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> jsonError(response, 401, "AUTENTICACAO_NECESSARIA", "Entre para continuar."))
                        .accessDeniedHandler((request, response, exception) -> jsonError(response, 403, "ACESSO_NEGADO", "Permissão insuficiente ou token CSRF inválido. Atualize a página e tente novamente.")))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'")))
                // CSRF continua habilitado, inclusive no login e no logout.
                .build();
    }
    private static void jsonError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"codigo\":\"" + code + "\",\"mensagem\":\"" + message + "\"}");
    }
}
