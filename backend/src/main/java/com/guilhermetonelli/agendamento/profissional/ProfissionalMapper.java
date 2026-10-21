package com.guilhermetonelli.agendamento.profissional;

import org.mapstruct.Mapper;

import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResumoResponse;
import com.guilhermetonelli.agendamento.servico.ServicoMapper;
import com.guilhermetonelli.agendamento.shared.config.MapStructConfig;

@Mapper(config = MapStructConfig.class, uses = ServicoMapper.class)
interface ProfissionalMapper {

    ProfissionalResponse paraResponse(Profissional profissional);

    ProfissionalResumoResponse paraResumo(Profissional profissional);
}
