package com.guilhermetonelli.agendamento.shared.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/** Documentação da API, disponível em /swagger-ui.html. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "API de Agendamento",
        version = "v1",
        description = "Cadastro de serviços, profissionais e clientes, disponibilidade e reservas sem conflito de horário."))
class OpenApiConfig {}
