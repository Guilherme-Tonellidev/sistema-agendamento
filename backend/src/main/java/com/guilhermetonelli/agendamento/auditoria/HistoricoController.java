package com.guilhermetonelli.agendamento.auditoria;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Só o administrador lê o histórico; a regra está no {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/agendamentos")
@Tag(name = "Histórico", description = "Linha do tempo de cada agendamento")
class HistoricoController {

    private final HistoricoService service;

    HistoricoController(HistoricoService service) {
        this.service = service;
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Quem criou, remarcou, cancelou, concluiu ou marcou falta, e quando, do mais antigo ao mais novo (só administrador)")
    List<EventoDoHistorico> historico(@PathVariable UUID id) {
        return service.historico(id);
    }
}
