package com.guilhermetonelli.agendamento.auditoria;

import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CANCELADO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CONCLUIDO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.CRIADO;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.NAO_COMPARECEU;
import static com.guilhermetonelli.agendamento.auditoria.TipoDeEvento.REMARCADO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guilhermetonelli.agendamento.agendamento.Agendamento;
import com.guilhermetonelli.agendamento.auditoria.EventoDoHistorico.Autor;
import com.guilhermetonelli.agendamento.cliente.ClienteService;
import com.guilhermetonelli.agendamento.profissional.ProfissionalService;
import com.guilhermetonelli.agendamento.seguranca.AutenticacaoService;
import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.shared.exception.RecursoNaoEncontradoException;

/**
 * Monta a linha do tempo de um agendamento comparando cada revisão do Envers com a anterior. O servidor deriva os
 * eventos, então a tela não precisa conhecer o formato das revisões.
 */
@Service
@Transactional(readOnly = true)
public class HistoricoService {

    private static final String SISTEMA = "Sistema";

    private final EntityManager em;
    private final ProfissionalService profissionalService;
    private final ClienteService clienteService;
    private final AutenticacaoService autenticacaoService;

    HistoricoService(
            EntityManager em,
            ProfissionalService profissionalService,
            ClienteService clienteService,
            AutenticacaoService autenticacaoService) {
        this.em = em;
        this.profissionalService = profissionalService;
        this.clienteService = clienteService;
        this.autenticacaoService = autenticacaoService;
    }

    public List<EventoDoHistorico> historico(UUID agendamentoId) {
        if (em.find(Agendamento.class, agendamentoId) == null) {
            throw new RecursoNaoEncontradoException("Agendamento", agendamentoId);
        }
        List<Mudanca> mudancas = mudancasDe(agendamentoId);
        Autores autores = new Autores(mudancas.stream().map(Mudanca::revisao).toList());
        return mudancas.stream()
                .map(m -> new EventoDoHistorico(
                        Instant.ofEpochMilli(m.revisao().getTimestamp()),
                        autores.de(m.revisao()),
                        m.tipo(), m.de(), m.para(), m.motivo()))
                .toList();
    }

    /**
     * Na ordem do número da revisão: o relógio pode repetir ou voltar, o número só cresce. Cada linha do Envers vem
     * como {@code {agendamento, revisão, tipo da revisão}}; o agendamento é uma cópia do estado naquela revisão e
     * só se leem campos simples dele, nunca profissional, serviço ou cliente.
     */
    private List<Mudanca> mudancasDe(UUID agendamentoId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = AuditReaderFactory.get(em).createQuery()
                .forRevisionsOfEntity(Agendamento.class, false, true)
                .add(AuditEntity.id().eq(agendamentoId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<Mudanca> mudancas = new ArrayList<>();
        Agendamento anterior = null;
        for (Object[] linha : linhas) {
            RevisionType tipoDaRevisao = (RevisionType) linha[2];
            if (tipoDaRevisao == RevisionType.DEL) {
                // Agendamento nunca é apagado; se um dia for, a revisão de remoção vem sem estado e não é evento.
                continue;
            }
            Agendamento atual = (Agendamento) linha[0];
            derivar(anterior, atual, (RevisaoDeAuditoria) linha[1], tipoDaRevisao).ifPresent(mudancas::add);
            anterior = atual;
        }
        return mudancas;
    }

    /**
     * No máximo um evento por revisão. Sem revisão anterior que não seja de criação (histórico de antes da auditoria),
     * a revisão é só o ponto de partida.
     */
    private static Optional<Mudanca> derivar(
            Agendamento anterior, Agendamento atual, RevisaoDeAuditoria revisao, RevisionType tipoDaRevisao) {
        if (tipoDaRevisao == RevisionType.ADD) {
            return Optional.of(new Mudanca(revisao, CRIADO, null, null, null));
        }
        if (anterior == null) {
            return Optional.empty();
        }
        if (!Objects.equals(anterior.getInicio(), atual.getInicio())) {
            return Optional.of(new Mudanca(revisao, REMARCADO, anterior.getInicio(), atual.getInicio(), null));
        }
        if (anterior.getStatus() != atual.getStatus()) {
            return switch (atual.getStatus()) {
                case CANCELADO -> Optional.of(new Mudanca(revisao, CANCELADO, null, null, atual.getMotivoCancelamento()));
                case CONCLUIDO -> Optional.of(new Mudanca(revisao, CONCLUIDO, null, null, null));
                case NAO_COMPARECEU -> Optional.of(new Mudanca(revisao, NAO_COMPARECEU, null, null, null));
                case CONFIRMADO -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    /** O que uma revisão mudou, ainda sem o nome do autor, que se busca em lote depois. */
    private record Mudanca(RevisaoDeAuditoria revisao, TipoDeEvento tipo, Instant de, Instant para, String motivo) {}

    /**
     * Nomes dos autores das revisões, buscados em lote (uma consulta por tipo de cadastro). Quem não está mais no
     * cadastro aparece pelo nome do papel, e sem usuário o autor é o sistema: nunca é erro.
     */
    private final class Autores {

        private final Map<UUID, String> profissionais;
        private final Map<UUID, String> clientes;
        private final Map<UUID, String> contas;

        Autores(List<RevisaoDeAuditoria> revisoes) {
            this.profissionais = profissionalService.nomesPorId(chavesDe(revisoes, Papel.PROFISSIONAL));
            this.clientes = clienteService.nomesPorId(chavesDe(revisoes, Papel.CLIENTE));
            this.contas = autenticacaoService.emailsPorId(chavesDe(revisoes, Papel.ADMIN));
        }

        Autor de(RevisaoDeAuditoria revisao) {
            Papel papel = papelDe(revisao);
            if (papel == null) {
                return new Autor(SISTEMA, null);
            }
            String nome = switch (papel) {
                case PROFISSIONAL -> profissionais.get(chaveDe(revisao, papel));
                case CLIENTE -> clientes.get(chaveDe(revisao, papel));
                case ADMIN -> contas.get(chaveDe(revisao, papel));
            };
            return new Autor(nome != null ? nome : rotuloDe(papel), papel);
        }

        private static Set<UUID> chavesDe(List<RevisaoDeAuditoria> revisoes, Papel papel) {
            return revisoes.stream()
                    .filter(revisao -> papelDe(revisao) == papel)
                    .map(revisao -> chaveDe(revisao, papel))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        private static Papel papelDe(RevisaoDeAuditoria revisao) {
            return revisao.getPapel() == null ? null : Papel.valueOf(revisao.getPapel());
        }

        /** Profissional e cliente se acham pelo perfil; o administrador não tem perfil, só a conta. */
        private static UUID chaveDe(RevisaoDeAuditoria revisao, Papel papel) {
            return papel == Papel.ADMIN ? revisao.getUsuarioId() : revisao.getPerfilId();
        }

        private static String rotuloDe(Papel papel) {
            return switch (papel) {
                case ADMIN -> "Administrador";
                case PROFISSIONAL -> "Profissional";
                case CLIENTE -> "Cliente";
            };
        }
    }
}
