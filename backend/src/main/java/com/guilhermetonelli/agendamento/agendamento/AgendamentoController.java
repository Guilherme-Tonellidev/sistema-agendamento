package com.guilhermetonelli.agendamento.agendamento;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
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
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
            @Valid @RequestBody AgendamentoRequest request, UriComponentsBuilder uri) {
        AgendamentoResponse criado = service.reservar(request);
        var local = uri.path("/api/agendamentos/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista agendamentos com filtros e paginação, em ordem cronológica")
    PagedModel<AgendamentoResponse> listar(
            @ParameterObject AgendamentoFiltro filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "inicio") Pageable pageable) {
        return new PagedModel<>(service.listar(filtro, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um agendamento")
    AgendamentoResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancela um agendamento confirmado que ainda não começou; o corpo com o motivo é opcional")
    AgendamentoResponse cancelar(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CancelamentoRequest request) {
        return service.cancelar(id, request == null ? null : request.motivo());
    }

    @PatchMapping("/{id}/reagendar")
    @Operation(summary = "Muda o horário, mantendo profissional e serviço")
    AgendamentoResponse reagendar(@PathVariable UUID id, @Valid @RequestBody ReagendamentoRequest request) {
        return service.reagendar(id, request);
    }

    @PatchMapping("/{id}/concluir")
    @Operation(summary = "Marca o atendimento como realizado")
    AgendamentoResponse concluir(@PathVariable UUID id) {
        return service.concluir(id);
    }

    @PatchMapping("/{id}/nao-compareceu")
    @Operation(summary = "Registra que o cliente não compareceu")
    AgendamentoResponse naoCompareceu(@PathVariable UUID id) {
        return service.registrarFalta(id);
    }
}
