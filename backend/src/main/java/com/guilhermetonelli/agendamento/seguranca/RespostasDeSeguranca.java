package com.guilhermetonelli.agendamento.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Erros dos filtros de segurança (401 e 403) acontecem antes dos controllers. Entregá-los ao resolver do
 * Spring MVC faz o {@code TratadorDeErros} responder no mesmo formato Problem Details do resto da API.
 */
class RespostasDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    RespostasDeSeguranca(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
        resolver.resolveException(request, response, null, ex);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        resolver.resolveException(request, response, null, ex);
    }
}
