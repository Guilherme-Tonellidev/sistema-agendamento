package com.guilhermetonelli.agendamento.servico;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.servico.dto.ServicoFiltro;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoResponse;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

@Service
@Transactional(readOnly = true)
public class ServicoService {

    private final ServicoRepository repository;
    private final ServicoMapper mapper;

    ServicoService(ServicoRepository repository, ServicoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ServicoResponse cadastrar(ServicoRequest request) {
        garantirNomeDisponivel(request.nome());
        Servico servico = new Servico(request.nome(), request.descricao(), request.duracaoMinutos(), request.preco());
        return mapper.paraResponse(repository.save(servico));
    }

    public Page<ServicoResponse> listar(ServicoFiltro filtro, Pageable pageable) {
        return repository.findAll(ServicoSpecifications.de(filtro), pageable).map(mapper::paraResponse);
    }

    public ServicoResponse buscar(UUID id) {
        return mapper.paraResponse(obter(id));
    }

    @Transactional
    public ServicoResponse atualizar(UUID id, ServicoRequest request) {
        Servico servico = obter(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().strip(), id)) {
            throw nomeEmUso(request.nome());
        }
        servico.atualizar(request.nome(), request.descricao(), request.duracaoMinutos(), request.preco());
        return mapper.paraResponse(servico);
    }

    @Transactional
    public void ativar(UUID id) {
        obter(id).ativar();
    }

    @Transactional
    public void inativar(UUID id) {
        obter(id).inativar();
    }

    /**
     * Carrega os serviços para vincular a um profissional. Todos precisam existir e estar ativos;
     * a mensagem de erro diz exatamente quais IDs são o problema.
     */
    public List<Servico> obterAtivos(Collection<UUID> ids) {
        Set<UUID> pedidos = new HashSet<>(ids);
        List<Servico> encontrados = repository.findAllById(pedidos);

        Set<UUID> inexistentes = new HashSet<>(pedidos);
        encontrados.forEach(servico -> inexistentes.remove(servico.getId()));
        if (!inexistentes.isEmpty()) {
            throw new RecursoNaoEncontradoException("Serviço", ordenados(inexistentes));
        }

        Set<UUID> inativos = encontrados.stream()
                .filter(servico -> !servico.isAtivo())
                .map(Servico::getId)
                .collect(Collectors.toSet());
        if (!inativos.isEmpty()) {
            throw new RegraDeNegocioException("Serviços inativos não podem ser vinculados: " + ordenados(inativos));
        }

        return encontrados;
    }

    private Servico obter(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Serviço", id));
    }

    private void garantirNomeDisponivel(String nome) {
        if (repository.existsByNomeIgnoreCase(nome.strip())) {
            throw nomeEmUso(nome);
        }
    }

    private static ConflitoException nomeEmUso(String nome) {
        return new ConflitoException("Já existe um serviço com o nome \"" + nome.strip() + "\".");
    }

    private static String ordenados(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().collect(Collectors.joining(", "));
    }
}
