package com.guilhermetonelli.agendamento.servico;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;
import com.guilhermetonelli.agendamento.servico.dto.ServicoFiltro;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.guilhermetonelli.agendamento.shared.web.Ordenacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/servicos")
@Tag(name = "Serviços", description = "Catálogo de serviços oferecidos")
class ServicoController {

    /** Campos pelos quais quem não é administrador pode ordenar a listagem. */
    private static final Set<String> CAMPOS_DA_VITRINE = Set.of("nome", "duracaoMinutos", "preco");

    private final ServicoService service;

    ServicoController(ServicoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra um serviço")
    ResponseEntity<ServicoResponse> cadastrar(@Valid @RequestBody ServicoRequest request, UriComponentsBuilder uri) {
        ServicoResponse criado = service.cadastrar(request);
        var local = uri.path("/api/servicos/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista serviços com filtros e paginação")
    PagedModel<ServicoResponse> listar(
            @ParameterObject ServicoFiltro filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable,
            @AuthenticationPrincipal UsuarioLogado eu) {
        if (eu != null && eu.admin()) {
            return new PagedModel<>(service.listar(filtro, pageable));
        }
        // Vitrine pública: serviços inativos são do administrador, e só os campos da vitrine ordenam a lista.
        Ordenacao.permitirSomente(pageable, CAMPOS_DA_VITRINE);
        return new PagedModel<>(service.listar(filtro.somenteAtivos(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um serviço pelo id")
    ServicoResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um serviço")
    ServicoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest request) {
        return service.atualizar(id, request);
    }

    @PatchMapping("/{id}/ativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Volta a oferecer o serviço")
    void ativar(@PathVariable UUID id) {
        service.ativar(id);
    }

    @PatchMapping("/{id}/inativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deixa de oferecer o serviço, sem apagar o histórico")
    void inativar(@PathVariable UUID id) {
        service.inativar(id);
    }
}
