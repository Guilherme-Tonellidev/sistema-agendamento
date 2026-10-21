package com.guilhermetonelli.agendamento.cliente.dto;

import java.util.UUID;

public record ClienteResponse(UUID id, String nome, String email, String telefone) {}
