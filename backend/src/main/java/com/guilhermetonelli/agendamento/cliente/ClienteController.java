package com.guilhermetonelli.agendamento.cliente;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.guilhermetonelli.agendamento.cliente.dto.ClienteFiltro;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes", description = "Quem reserva os horários")
class ClienteController {

    private final ClienteService service;

    ClienteController(ClienteService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra um cliente sem conta de acesso, como quem agenda pelo balcão")
    ResponseEntity<ClienteResponse> cadastrar(@Valid @RequestBody ClienteRequest request, UriComponentsBuilder uri) {
        ClienteResponse criado = service.cadastrar(request);
        var local = uri.path("/api/clientes/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista clientes com filtros e paginação")
    PagedModel<ClienteResponse> listar(
            @ParameterObject ClienteFiltro filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listar(filtro, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cliente (o próprio cliente ou um administrador)")
    ClienteResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirCliente(id);
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de contato (o e-mail de login não muda)")
    ClienteResponse atualizar(
            @PathVariable UUID id, @Valid @RequestBody ClienteRequest request, @AuthenticationPrincipal UsuarioLogado eu) {
        eu.garantirCliente(id);
        return service.atualizar(id, request);
    }
}
