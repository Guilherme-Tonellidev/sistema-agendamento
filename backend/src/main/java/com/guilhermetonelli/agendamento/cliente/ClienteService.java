package com.guilhermetonelli.agendamento.cliente;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.cliente.dto.ClienteFiltro;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteRequest;
import com.guilhermetonelli.agendamento.cliente.dto.ClienteResponse;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository repository;
    private final ClienteMapper mapper;

    ClienteService(ClienteRepository repository, ClienteMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ClienteResponse cadastrar(ClienteRequest request) {
        String email = Textos.normalizarEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw emailEmUso(email);
        }
        Cliente cliente = new Cliente(request.nome(), email, request.telefone());
        return mapper.paraResponse(repository.save(cliente));
    }

    /** Para o encaixe de quem chegou sem reserva. */
    @Transactional
    public Cliente cadastrarSemConta(String nome, String telefone) {
        return repository.save(Cliente.semConta(nome, telefone));
    }

    public Page<ClienteResponse> listar(ClienteFiltro filtro, Pageable pageable) {
        return repository.findAll(ClienteSpecifications.de(filtro), pageable).map(mapper::paraResponse);
    }

    public ClienteResponse buscar(UUID id) {
        return mapper.paraResponse(obter(id));
    }

    @Transactional
    public ClienteResponse atualizar(UUID id, ClienteRequest request) {
        Cliente cliente = obter(id);
        String email = Textos.normalizarEmail(request.email());
        if (repository.existsByEmailAndIdNot(email, id)) {
            throw emailEmUso(email);
        }
        cliente.atualizar(request.nome(), email, request.telefone());
        return mapper.paraResponse(cliente);
    }

    public Cliente obter(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }

    private static ConflitoException emailEmUso(String email) {
        return new ConflitoException("Já existe um cliente com o e-mail " + email + ".");
    }
}
