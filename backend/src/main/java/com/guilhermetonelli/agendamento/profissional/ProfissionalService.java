package com.guilhermetonelli.agendamento.profissional;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalFiltro;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalRequest;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ProfissionalResumoResponse;
import com.guilhermetonelli.agendamento.profissional.dto.ServicosDoProfissionalRequest;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.shared.domain.Textos;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class ProfissionalService {

    private final ProfissionalRepository repository;
    private final ProfissionalMapper mapper;
    private final ServicoService servicoService;

    ProfissionalService(ProfissionalRepository repository, ProfissionalMapper mapper, ServicoService servicoService) {
        this.repository = repository;
        this.mapper = mapper;
        this.servicoService = servicoService;
    }

    @Transactional
    public ProfissionalResponse cadastrar(ProfissionalRequest request) {
        String email = Textos.normalizarEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw emailEmUso(email);
        }
        Profissional profissional = new Profissional(request.nome(), email, request.telefone());
        return mapper.paraResponse(repository.save(profissional));
    }

    public Page<ProfissionalResumoResponse> listar(ProfissionalFiltro filtro, Pageable pageable) {
        return repository.findAll(ProfissionalSpecifications.de(filtro), pageable).map(mapper::paraResumo);
    }

    public ProfissionalResponse buscar(UUID id) {
        return mapper.paraResponse(obterComServicos(id));
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID id, ProfissionalRequest request) {
        Profissional profissional = obterComServicos(id);
        String email = Textos.normalizarEmail(request.email());
        if (repository.existsByEmailAndIdNot(email, id)) {
            throw emailEmUso(email);
        }
        profissional.atualizar(request.nome(), email, request.telefone());
        return mapper.paraResponse(profissional);
    }

    @Transactional
    public ProfissionalResponse definirServicos(UUID id, ServicosDoProfissionalRequest request) {
        Profissional profissional = obterComServicos(id);
        profissional.definirServicos(servicoService.obterAtivos(request.servicoIds()));
        return mapper.paraResponse(profissional);
    }

    @Transactional
    public void ativar(UUID id) {
        obter(id).ativar();
    }

    @Transactional
    public void inativar(UUID id) {
        obter(id).inativar();
    }

    public Profissional obterComServicos(UUID id) {
        return repository.findComServicosById(id).orElseThrow(() -> naoEncontrado(id));
    }

    /** Trava o profissional até o fim da transação, para alterações concorrentes na agenda dele não se misturarem. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Profissional obterComTrava(UUID id) {
        return repository.findParaAtualizarById(id).orElseThrow(() -> naoEncontrado(id));
    }

    public boolean existe(UUID id) {
        return repository.existsById(id);
    }

    public void garantirQueExiste(UUID id) {
        if (!repository.existsById(id)) {
            throw naoEncontrado(id);
        }
    }

    private Profissional obter(UUID id) {
        return repository.findById(id).orElseThrow(() -> naoEncontrado(id));
    }

    private static RecursoNaoEncontradoException naoEncontrado(UUID id) {
        return new RecursoNaoEncontradoException("Profissional", id);
    }

    private static ConflitoException emailEmUso(String email) {
        return new ConflitoException("Já existe um profissional com o e-mail " + email + ".");
    }
}
