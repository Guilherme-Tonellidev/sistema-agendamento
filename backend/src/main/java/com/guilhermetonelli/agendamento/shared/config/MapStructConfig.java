package com.guilhermetonelli.agendamento.shared.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Configuração comum dos mappers. Um campo de resposta sem origem quebra a compilação, em vez de
 * sair {@code null} na API sem ninguém perceber.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapStructConfig {}
