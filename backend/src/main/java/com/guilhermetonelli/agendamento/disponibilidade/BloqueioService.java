package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioFiltro;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioRequest;
import com.guilhermetonelli.agendamento.disponibilidade.dto.BloqueioResponse;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class BloqueioService {

    private final BloqueioRepository repository;
    private final DisponibilidadeMapper mapper;
    private final ProfissionalService profissionalService;
    private final AgendamentoProperties properties;

    BloqueioService(
            BloqueioRepository repository,
            DisponibilidadeMapper mapper,
            ProfissionalService profissionalService,
            AgendamentoProperties properties) {
        this.repository = repository;
        this.mapper = mapper;
        this.profissionalService = profissionalService;
        this.properties = properties;
    }

    @Transactional
    public BloqueioResponse bloquear(UUID profissionalId, BloqueioRequest request) {
        profissionalService.garantirQueExiste(profissionalId);
        Bloqueio bloqueio = new Bloqueio(profissionalId, request.inicio(), request.fim(), request.motivo());
        return mapper.paraResponse(repository.save(bloqueio));
    }

    public List<BloqueioResponse> listar(UUID profissionalId, BloqueioFiltro filtro) {
        profissionalService.garantirQueExiste(profissionalId);
        FaixaDeDatas faixa = FaixaDeDatas.entre(filtro.de(), filtro.ate(), properties.fusoHorario());

        return repository.findAll(BloqueioSpecifications.sobrepostos(profissionalId, faixa.inicio(), faixa.fim()), Sort.by("inicio"))
                .stream()
                .map(mapper::paraResponse)
                .toList();
    }

    @Transactional
    public void remover(UUID profissionalId, UUID bloqueioId) {
        Bloqueio bloqueio = repository.findByIdAndProfissionalId(bloqueioId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio", bloqueioId));
        repository.delete(bloqueio);
    }

    List<Periodo> bloqueados(UUID profissionalId, Periodo periodo) {
        return repository.findAll(BloqueioSpecifications.sobrepostos(profissionalId, periodo.inicio(), periodo.fim()))
                .stream()
                .map(Bloqueio::periodo)
                .toList();
    }
}
