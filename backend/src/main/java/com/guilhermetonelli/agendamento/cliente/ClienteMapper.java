package com.guilhermetonelli.agendamento.cliente;

import org.mapstruct.Mapper;

import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.shared.config.MapStructConfig;

@Mapper(config = MapStructConfig.class)
interface ClienteMapper {

    ClienteResponse paraResponse(Cliente cliente);
}
