package com.guilhermetonelli.agendamento.shared.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Documentação da API, disponível em /swagger-ui.html. O botão "Authorize" recebe o token de acesso. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "API de Agendamento",
                version = "v1",
                description = "Cadastro de serviços, profissionais e clientes, disponibilidade e reservas sem conflito de horário."),
        security = @SecurityRequirement(name = "token"))
@SecurityScheme(name = "token", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {}
