package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.Instant;
import java.time.LocalDate;
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
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;
import com.guilhermetonelli.agendamento.shared.exception.RegraDeNegocioException;

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
        garantirNaFaixa(filtro.de());
        garantirNaFaixa(filtro.ate());
        if (filtro.de() != null && filtro.ate() != null && filtro.de().isAfter(filtro.ate())) {
            throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        }
        Instant inicio = inicioDoDia(filtro.de());
        Instant fim = filtro.ate() == null ? null : inicioDoDia(filtro.ate().plusDays(1));

        return repository.findAll(BloqueioSpecifications.sobrepostos(profissionalId, inicio, fim), Sort.by("inicio"))
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

    private static void garantirNaFaixa(LocalDate data) {
        if (data != null && (data.getYear() < Bloqueio.ANO_MINIMO || data.getYear() > Bloqueio.ANO_MAXIMO)) {
            throw new RegraDeNegocioException(
                    "As datas devem estar entre os anos %d e %d.".formatted(Bloqueio.ANO_MINIMO, Bloqueio.ANO_MAXIMO));
        }
    }

    private Instant inicioDoDia(LocalDate data) {
        return data == null ? null : data.atStartOfDay(properties.fusoHorario()).toInstant();
    }
}
