package com.guilhermetonelli.agendamento.disponibilidade.dto;

import java.time.Instant;
import java.util.UUID;

public record BloqueioResponse(UUID id, Instant inicio, Instant fim, String motivo) {}
