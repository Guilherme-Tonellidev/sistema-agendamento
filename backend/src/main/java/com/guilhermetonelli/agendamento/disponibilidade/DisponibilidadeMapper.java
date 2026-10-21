package com.guilhermetonelli.agendamento.disponibilidade;

import org.mapstruct.Mapper;

import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.IntervaloDeJornadaDto;
import com.guilhermetonelli.agendamento.shared.config.MapStructConfig;

@Mapper(config = MapStructConfig.class)
interface DisponibilidadeMapper {

    IntervaloDeJornadaDto paraDto(IntervaloDeJornada intervalo);

    BloqueioResponse paraResponse(Bloqueio bloqueio);

    HorarioLivreResponse paraResponse(Periodo horario);
}
