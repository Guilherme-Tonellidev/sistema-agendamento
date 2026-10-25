package com.guilhermetonelli.agendamento.notificacao;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guilhermetonelli.agendamento.notificacao.dto.NotificacoesResponse;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Só o barbeiro acessa, e só as suas: a regra por papel está em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/notificacoes")
@Tag(name = "Notificações", description = "Sino do barbeiro: avisos de reservas, remarcações e cancelamentos")
class NotificacaoController {

    private final NotificacaoService service;

    NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "As 20 notificações mais recentes e quantas ainda não foram lidas")
    NotificacoesResponse listar(@AuthenticationPrincipal UsuarioLogado eu) {
        return service.listar(eu.profissionalId());
    }

    @PatchMapping("/{id}/ler")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca uma notificação como lida")
    void ler(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu) {
        service.marcarComoLida(eu.profissionalId(), id);
    }

    @PatchMapping("/ler-todas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca todas como lidas")
    void lerTodas(@AuthenticationPrincipal UsuarioLogado eu) {
        service.marcarTodasComoLidas(eu.profissionalId());
    }
}
