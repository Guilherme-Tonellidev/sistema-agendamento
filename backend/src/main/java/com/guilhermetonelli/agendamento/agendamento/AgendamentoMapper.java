package com.guilhermetonelli.agendamento.agendamento;

import org.mapstruct.Mapper;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.cliente.ClienteMapper;
import com.guilhermetonelli.agendamento.profissional.ProfissionalMapper;
import com.guilhermetonelli.agendamento.servico.ServicoMapper;
import com.guilhermetonelli.agendamento.shared.config.MapStructConfig;

@Mapper(config = MapStructConfig.class, uses = {ProfissionalMapper.class, ServicoMapper.class, ClienteMapper.class})
interface AgendamentoMapper {

    AgendamentoResponse paraResponse(Agendamento agendamento);
}
