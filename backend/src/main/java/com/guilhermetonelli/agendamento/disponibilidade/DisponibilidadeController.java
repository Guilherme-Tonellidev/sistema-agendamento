package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioFiltro;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.HorarioLivreResponse;
import com.guilhermetonelli.agendamento.disponibilidade.dto.JornadaDto;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/profissionais/{id}")
@Tag(name = "Disponibilidade", description = "Jornada, bloqueios e horários livres de cada profissional")
class DisponibilidadeController {

    private final JornadaService jornadaService;
    private final BloqueioService bloqueioService;
    private final DisponibilidadeService disponibilidadeService;

    DisponibilidadeController(
            JornadaService jornadaService,
            BloqueioService bloqueioService,
            DisponibilidadeService disponibilidadeService) {
        this.jornadaService = jornadaService;
        this.bloqueioService = bloqueioService;
        this.disponibilidadeService = disponibilidadeService;
    }

    @GetMapping("/jornada")
    @Operation(summary = "Jornada semanal do profissional, em horário local do negócio")
    JornadaDto buscarJornada(@PathVariable UUID id) {
        return jornadaService.buscar(id);
    }

    @PutMapping("/jornada")
    @Operation(summary = "Substitui a jornada semanal inteira")
    JornadaDto definirJornada(
            @PathVariable UUID id, @Valid @RequestBody JornadaDto jornada, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirAgendaDe(id);
        return jornadaService.definir(id, jornada);
    }

    @PostMapping("/bloqueios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bloqueia um período da agenda (folga, férias, ausência)")
    BloqueioResponse bloquear(
            @PathVariable UUID id, @Valid @RequestBody BloqueioRequest request, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirAgendaDe(id);
        return bloqueioService.bloquear(id, request);
    }

    @GetMapping("/bloqueios")
    @Operation(summary = "Lista os bloqueios que tocam o período, em ordem cronológica (o motivo é privado)")
    List<BloqueioResponse> listarBloqueios(
            @PathVariable UUID id, @ParameterObject BloqueioFiltro filtro, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirAgendaDe(id);
        return bloqueioService.listar(id, filtro);
    }

    @DeleteMapping("/bloqueios/{bloqueioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um bloqueio")
    void removerBloqueio(@PathVariable UUID id, @PathVariable UUID bloqueioId, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirAgendaDe(id);
        bloqueioService.remover(id, bloqueioId);
    }

    @GetMapping("/horarios-livres")
    @Operation(summary = "Horários livres do profissional para um serviço num dia")
    List<HorarioLivreResponse> horariosLivres(
            @PathVariable UUID id,
            @RequestParam UUID servicoId,
            @Parameter(description = "Dia no fuso do negócio", example = "2026-10-21") @RequestParam LocalDate data) {
        return disponibilidadeService.horariosLivres(id, servicoId, data);
    }

    @GetMapping("/horarios-livres/encaixe")
    @Operation(summary = "Horários livres a partir de agora, para encaixar quem chegou sem reserva (profissional ou administrador)")
    List<HorarioLivreResponse> horariosParaEncaixe(
            @PathVariable UUID id,
            @RequestParam UUID servicoId,
            @Parameter(description = "Dia no fuso do negócio", example = "2026-10-21") @RequestParam LocalDate data,
            @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirAgendaDe(id);
        return disponibilidadeService.horariosParaEncaixe(id, servicoId, data);
    }
}
