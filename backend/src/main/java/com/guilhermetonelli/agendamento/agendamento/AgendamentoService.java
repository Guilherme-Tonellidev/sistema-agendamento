package com.guilhermetonelli.agendamento.agendamento;

import static com.guilhermetonelli.agendamento.agendamento.AgendamentoSpecifications.ativosDoCliente;
import static com.guilhermetonelli.agendamento.agendamento.AgendamentoSpecifications.ativosDoProfissional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PSQLException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoFiltro;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.dto.AgendamentoResponse;
import com.guilhermetonelli.agendamento.agendamento.dto.ReagendamentoRequest;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCancelado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;
import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoReagendado;
import com.guilhermetonelli.agendamento.cliente.Cliente;
import com.guilhermetonelli.agendamento.cliente.ClienteService;
import com.guilhermetonelli.agendamento.disponibilidade.DisponibilidadeService;
import com.guilhermetonelli.agendamento.disponibilidade.Periodo;
import com.guilhermetonelli.agendamento.profissional.Profissional;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.servico.Servico;
import com.guilhermetonelli.agendamento.servico.ServicoService;
import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties;
import com.guilhermetonelli.agendamento.shared.domain.FaixaDeDatas;
import com.guilhermetonelli.agendamento.shared.exception.ConflitoException;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly = true)
public class AgendamentoService {

    private static final String PROFISSIONAL_OCUPADO =
            "Horário indisponível: o profissional já tem um agendamento nesse horário.";
    private static final String CLIENTE_OCUPADO =
            "Horário indisponível: o cliente já tem um agendamento nesse horário.";
    private static final String HORARIO_DISPUTADO =
            "Horário indisponível: outra reserva foi feita para esse horário ao mesmo tempo.";

    private static final String VIOLACAO_DE_EXCLUSAO = "23P01";

    /** Constraints de exclusão da tabela, com a mensagem de cada uma. */
    private static final Map<String, String> CONFLITOS_NO_BANCO = Map.of(
            "ex_agendamento_profissional", PROFISSIONAL_OCUPADO,
            "ex_agendamento_cliente", CLIENTE_OCUPADO);

    private final AgendamentoRepository repository;
    private final AgendamentoMapper mapper;
    private final ProfissionalService profissionalService;
    private final ServicoService servicoService;
    private final ClienteService clienteService;
    private final DisponibilidadeService disponibilidadeService;
    private final ApplicationEventPublisher eventos;
    private final AgendamentoProperties properties;
    private final Clock clock;

    AgendamentoService(
            AgendamentoRepository repository,
            AgendamentoMapper mapper,
            ProfissionalService profissionalService,
            ServicoService servicoService,
            ClienteService clienteService,
            DisponibilidadeService disponibilidadeService,
            ApplicationEventPublisher eventos,
            AgendamentoProperties properties,
            Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.profissionalService = profissionalService;
        this.servicoService = servicoService;
        this.clienteService = clienteService;
        this.disponibilidadeService = disponibilidadeService;
        this.eventos = eventos;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * A trava no profissional serializa reservas, bloqueios e mudanças de jornada da mesma agenda: a checagem
     * de disponibilidade e a gravação acontecem sem ninguém mudar a agenda no meio.
     */
    @Transactional
    public AgendamentoResponse reservar(AgendamentoRequest request) {
        Profissional profissional = profissionalService.obterComTrava(request.profissionalId());
        Servico servico = servicoService.obter(request.servicoId());
        Cliente cliente = clienteService.obter(request.clienteId());
        Periodo horario = disponibilidadeService.garantirHorarioNaAgenda(profissional, servico, request.inicio());
        garantirSemConflito(profissional.getId(), cliente.getId(), horario, Specification.unrestricted());

        Agendamento agendamento = salvar(new Agendamento(profissional, servico, cliente, horario));
        eventos.publishEvent(new AgendamentoCriado(
                agendamento.getId(), profissional.getId(), cliente.getId(), horario.inicio(), horario.fim()));
        return mapper.paraResponse(agendamento);
    }

    public Page<AgendamentoResponse> listar(AgendamentoFiltro filtro, Pageable pageable) {
        FaixaDeDatas faixa = FaixaDeDatas.entre(filtro.de(), filtro.ate(), properties.fusoHorario());
        // O id desempata agendamentos com o mesmo início, para nenhum item repetir ou sumir entre as páginas.
        var estavel = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
        return repository.findAll(AgendamentoSpecifications.de(filtro, faixa), estavel).map(mapper::paraResponse);
    }

    public AgendamentoResponse buscar(UUID id) {
        return mapper.paraResponse(obter(id));
    }

    @Transactional
    public AgendamentoResponse cancelar(UUID id, String motivo) {
        Agendamento agendamento = obter(id);
        agendamento.cancelar(motivo, Instant.now(clock), properties.antecedenciaMinimaCancelamento());
        repository.flush();
        eventos.publishEvent(new AgendamentoCancelado(
                id,
                profissionalId(agendamento),
                clienteId(agendamento),
                agendamento.getInicio(),
                agendamento.getMotivoCancelamento()));
        return mapper.paraResponse(agendamento);
    }

    /** Mesmas regras de uma reserva nova, com o mesmo profissional e serviço; o horário antigo não conflita. */
    @Transactional
    public AgendamentoResponse reagendar(UUID id, ReagendamentoRequest request) {
        Agendamento agendamento = obter(id);
        profissionalService.obterComTrava(profissionalId(agendamento));
        Instant agora = Instant.now(clock);
        agendamento.garantirQuePodeReagendar(agora);

        Periodo horario = disponibilidadeService.garantirHorarioNaAgenda(
                agendamento.getProfissional(), agendamento.getServico(), request.inicio());
        garantirSemConflito(
                profissionalId(agendamento), clienteId(agendamento), horario, AgendamentoSpecifications.exceto(id));

        Instant inicioAnterior = agendamento.getInicio();
        agendamento.reagendar(horario, agora);
        salvar(agendamento);
        eventos.publishEvent(new AgendamentoReagendado(
                id, profissionalId(agendamento), clienteId(agendamento), inicioAnterior, horario.inicio(), horario.fim()));
        return mapper.paraResponse(agendamento);
    }

    @Transactional
    public AgendamentoResponse concluir(UUID id) {
        Agendamento agendamento = obter(id);
        agendamento.concluir(Instant.now(clock));
        return mapper.paraResponse(agendamento);
    }

    @Transactional
    public AgendamentoResponse registrarFalta(UUID id) {
        Agendamento agendamento = obter(id);
        agendamento.registrarFalta(Instant.now(clock));
        return mapper.paraResponse(agendamento);
    }

    private void garantirSemConflito(
            UUID profissionalId, UUID clienteId, Periodo horario, Specification<Agendamento> outros) {
        if (repository.exists(ativosDoProfissional(profissionalId, horario).and(outros))) {
            throw new ConflitoException(PROFISSIONAL_OCUPADO);
        }
        if (repository.exists(ativosDoCliente(clienteId, horario).and(outros))) {
            throw new ConflitoException(CLIENTE_OCUPADO);
        }
    }

    /**
     * A checagem prévia dá a mensagem amigável; quando duas reservas chegam ao mesmo tempo, quem decide
     * são as constraints de exclusão, conferidas aqui no flush. Como o PostgreSQL grava a linha antes de
     * procurar conflitos, duas gravações simultâneas podem esperar uma pela outra, e o banco aborta uma
     * delas por deadlock: também é um conflito de horário.
     */
    private Agendamento salvar(Agendamento agendamento) {
        try {
            return repository.saveAndFlush(agendamento);
        } catch (DataIntegrityViolationException ex) {
            throw conflitoNoBanco(ex);
        } catch (PessimisticLockingFailureException ex) {
            throw new ConflitoException(HORARIO_DISPUTADO);
        }
    }

    /** Usa o nome estruturado da constraint, que não depende do idioma das mensagens do servidor. */
    private static RuntimeException conflitoNoBanco(DataIntegrityViolationException ex) {
        if (ex.getMostSpecificCause() instanceof PSQLException sql
                && VIOLACAO_DE_EXCLUSAO.equals(sql.getSQLState())
                && sql.getServerErrorMessage() != null) {
            String mensagem = CONFLITOS_NO_BANCO.get(sql.getServerErrorMessage().getConstraint());
            if (mensagem != null) {
                return new ConflitoException(mensagem);
            }
        }
        return ex;
    }

    private Agendamento obter(UUID id) {
        return repository.findDetalhadoById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento", id));
    }

    private static UUID profissionalId(Agendamento agendamento) {
        return agendamento.getProfissional().getId();
    }

    private static UUID clienteId(Agendamento agendamento) {
        return agendamento.getCliente().getId();
    }
}
