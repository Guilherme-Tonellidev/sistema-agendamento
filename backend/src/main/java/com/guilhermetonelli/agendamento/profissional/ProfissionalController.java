package com.guilhermetonelli.agendamento.profissional;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalFiltro;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResumoResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/profissionais")
@Tag(name = "Profissionais", description = "Quem presta os serviços")
class ProfissionalController {

    private final ProfissionalService service;

    ProfissionalController(ProfissionalService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra um profissional")
    ResponseEntity<ProfissionalResponse> cadastrar(
            @Valid @RequestBody ProfissionalRequest request, UriComponentsBuilder uri) {
        ProfissionalResponse criado = service.cadastrar(request);
        var local = uri.path("/api/profissionais/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista profissionais com filtros e paginação")
    PagedModel<ProfissionalResumoResponse> listar(
            @ParameterObject ProfissionalFiltro filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listar(filtro, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um profissional, com os serviços que ele atende")
    ProfissionalResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um profissional")
    ProfissionalResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequest request) {
        return service.atualizar(id, request);
    }

    @PutMapping("/{id}/servicos")
    @Operation(summary = "Define quais serviços o profissional atende (substitui a lista atual)")
    ProfissionalResponse definirServicos(
            @PathVariable UUID id, @Valid @RequestBody ServicosDoProfissionalRequest request) {
        return service.definirServicos(id, request);
    }

    @PatchMapping("/{id}/ativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reativa o profissional")
    void ativar(@PathVariable UUID id) {
        service.ativar(id);
    }

    @PatchMapping("/{id}/inativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa o profissional, sem apagar o histórico")
    void inativar(@PathVariable UUID id) {
        service.inativar(id);
    }
}
