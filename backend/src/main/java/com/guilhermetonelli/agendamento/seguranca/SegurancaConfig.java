package com.guilhermetonelli.agendamento.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * API stateless: cada requisição traz um JWT no cabeçalho {@code Authorization}. Tudo é negado por padrão;
 * as regras abaixo liberam o que é público e restringem o que é só de administrador. Regras que dependem
 * do dono do recurso (a agenda de quem, o agendamento de quem) ficam nos services, com {@link UsuarioLogado}.
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, RespostasDeSeguranca respostas) throws Exception {
        return http
                // Sem sessão nem cookie de autenticação nas rotas protegidas, não há CSRF a explorar.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cadastro", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        // Catálogo e horários livres são vitrine: qualquer um consulta antes de criar conta.
                        .requestMatchers(HttpMethod.GET,
                                "/api/servicos", "/api/servicos/*",
                                "/api/profissionais", "/api/profissionais/*",
                                "/api/profissionais/*/jornada", "/api/profissionais/*/horarios-livres")
                        .permitAll()
                        .requestMatchers("/api/servicos/**", "/api/clientes").hasRole("ADMIN")
                        .requestMatchers("/api/notificacoes/**").hasRole("PROFISSIONAL")
                        .requestMatchers(HttpMethod.POST, "/api/profissionais").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/profissionais/*", "/api/profissionais/*/servicos", "/api/profissionais/*/acesso")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/profissionais/*/ativar", "/api/profissionais/*/inativar")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            UsuarioLogado usuario = UsuarioLogado.de(token);
                            return UsernamePasswordAuthenticationToken.authenticated(
                                    usuario, token, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.papel())));
                        }))
                        .authenticationEntryPoint(respostas))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SegurancaProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.chaveJwt()));
    }

    /** Valida assinatura, emissor, tipo e validade. A validade usa o mesmo {@link Clock} da aplicação. */
    @Bean
    JwtDecoder jwtDecoder(SegurancaProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.chaveJwt())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator validade = new JwtTimestampValidator(Duration.ZERO);
        validade.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                validade, new JwtIssuerValidator(properties.jwtEmissor()), JwtTypeValidator.jwt()));
        return decoder;
    }

    /** BCrypt por padrão, com prefixo do algoritmo no hash: dá para trocar de algoritmo sem invalidar senhas. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SegurancaProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.origensPermitidas());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Location"));
        // Necessário para o navegador enviar o cookie do refresh token.
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", cors);
        return fonte;
    }

    @Bean
    RespostasDeSeguranca respostasDeSeguranca(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new RespostasDeSeguranca(resolver);
    }
}
