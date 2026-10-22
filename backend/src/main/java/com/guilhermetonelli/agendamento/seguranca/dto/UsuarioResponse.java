package com.guilhermetonelli.agendamento.seguranca.dto;

import java.util.UUID;

import com.guilhermetonelli.agendamento.seguranca.Papel;

public record UsuarioResponse(UUID id, String email, Papel papel, UUID profissionalId, UUID clienteId) {}
