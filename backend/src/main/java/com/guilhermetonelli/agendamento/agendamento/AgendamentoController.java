package com.guilhermetonelli.agendamento.agendamento;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoFiltro;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.agendamento.dto.CancelamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.EncaixeRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Clientes veem e mexem só nos próprios agendamentos; profissionais, só nos da própria agenda. */
@RestController
@RequestMapping("/api/agendamentos")
@Tag(name = "Agendamentos", description = "Reserva, cancelamento e reagendamento de horários")
class AgendamentoController {

    private final AgendamentoService service;

    AgendamentoController(AgendamentoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Reserva um horário livre")
    ResponseEntity<AgendamentoResponse> reservar(
            @Valid @RequestBody AgendamentoRequest request,
            @AuthenticationPrincipal UsuarioLogado eu,
            UriComponentsBuilder uri) {
        return criado(service.reservar(request, eu), uri);
    }

    @PostMapping("/encaixe")
    @Operation(summary = "Encaixa quem chegou sem reserva, sem antecedência mínima (profissional ou administrador)")
    ResponseEntity<AgendamentoResponse> encaixar(
            @Valid @RequestBody EncaixeRequest request,
            @AuthenticationPrincipal UsuarioLogado eu,
            UriComponentsBuilder uri) {
        return criado(service.encaixar(request, eu), uri);
    }

    @GetMapping
    @Operation(summary = "Lista os agendamentos visíveis para quem pede, com filtros e paginação")
    PagedModel<AgendamentoResponse> listar(
            @ParameterObject AgendamentoFiltro filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "inicio") Pageable pageable,
            @AuthenticationPrincipal UsuarioLogado eu) {
        return new PagedModel<>(service.listar(filtro, pageable, eu));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um agendamento")
    AgendamentoResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.buscar(id, eu);
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancela um agendamento que ainda não começou; o corpo com o motivo é opcional")
    AgendamentoResponse cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelamentoRequest request,
            @AuthenticationPrincipal UsuarioLogado eu) {
        return service.cancelar(id, request == null ? null : request.motivo(), eu);
    }

    @PatchMapping("/{id}/reagendar")
    @Operation(summary = "Muda o horário, mantendo profissional e serviço")
    AgendamentoResponse reagendar(
            @PathVariable UUID id, @Valid @RequestBody ReagendamentoRequest request, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.reagendar(id, request, eu);
    }

    @PatchMapping("/{id}/concluir")
    @Operation(summary = "Marca o atendimento como realizado (profissional ou administrador)")
    AgendamentoResponse concluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.concluir(id, eu);
    }

    @PatchMapping("/{id}/nao-compareceu")
    @Operation(summary = "Registra que o cliente não compareceu (profissional ou administrador)")
    AgendamentoResponse naoCompareceu(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.registrarFalta(id, eu);
    }

    private static ResponseEntity<AgendamentoResponse> criado(
            AgendamentoResponse agendamento, UriComponentsBuilder uri) {
        var local = uri.path("/api/agendamentos/{id}").buildAndExpand(agendamento.id()).toUri();
        return ResponseEntity.created(local).body(agendamento);
    }
}
