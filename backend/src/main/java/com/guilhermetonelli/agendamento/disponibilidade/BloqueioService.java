package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.List;
import java.util.Map;
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
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class BloqueioService {

    private final BloqueioRepository repository;
    private final DisponibilidadeMapper mapper;
    private final ProfissionalService profissionalService;
    private final AgendamentosAtivos agendamentosAtivos;
    private final AgendamentoProperties properties;

    BloqueioService(
            BloqueioRepository repository,
            DisponibilidadeMapper mapper,
            ProfissionalService profissionalService,
            AgendamentosAtivos agendamentosAtivos,
            AgendamentoProperties properties) {
        this.repository = repository;
        this.mapper = mapper;
        this.profissionalService = profissionalService;
        this.agendamentosAtivos = agendamentosAtivos;
        this.properties = properties;
    }

    /**
     * Recusa bloquear um período com reservas: nenhum cliente perde o horário sem ser avisado. A trava no
     * profissional serializa bloqueios e reservas da mesma agenda, então uma reserva simultânea não escapa.
     */
    @Transactional
    public BloqueioResponse bloquear(UUID profissionalId, BloqueioRequest request) {
        profissionalService.obterComTrava(profissionalId);
        Bloqueio bloqueio = new Bloqueio(profissionalId, request.inicio(), request.fim(), request.motivo());

        List<AgendamentoAtivo> afetados = agendamentosAtivos.doProfissional(profissionalId, bloqueio.periodo());
        if (!afetados.isEmpty()) {
            String quantos = afetados.size() == 1 ? "1 agendamento" : afetados.size() + " agendamentos";
            throw new ConflitoException(
                    "O período tem " + quantos + ". Cancele ou remarque antes de bloquear.",
                    Map.of("agendamentos", afetados.stream().map(a -> Map.of(
                            "id", a.id(), "inicio", a.horario().inicio(), "fim", a.horario().fim())).toList()));
        }
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
