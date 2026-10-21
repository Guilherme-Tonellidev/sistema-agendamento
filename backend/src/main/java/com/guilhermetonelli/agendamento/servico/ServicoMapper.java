package com.guilhermetonelli.agendamento.servico;

import org.mapstruct.Mapper;

import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResumoResponse;
import com.guilhermetonelli.agendamento.shared.config.MapStructConfig;

@Mapper(config = MapStructConfig.class)
public interface ServicoMapper {

    ServicoResponse paraResponse(Servico servico);

    ServicoResumoResponse paraResumo(Servico servico);
}
