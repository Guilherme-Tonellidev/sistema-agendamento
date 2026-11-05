package com.guilhermetonelli.agendamento.auditoria;

import static com.guilhermetonelli.agendamento.Cenario.AS_8H;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.Cenario.Cadastro;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.agendamento.Agendamento;
import com.guilhermetonelli.agendamento.shared.domain.Antecedencia;

/** O Envers grava uma revisão por mudança no agendamento, com o autor lido do token; só o lembrete mudar não conta. */
class RevisoesDoAgendamentoTest extends IntegracaoTest {

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private EntityManager em;

    private Cenario cenario;
    private UUID ana;
    private Cadastro carlos;

    @BeforeEach
    void cadastrar() {
        cenario = new Cenario(api);
        ana = cenario.barbeiro("Ana Souza");
        carlos = cenario.cliente("Carlos Lima");
    }

    @Test
    void reservaGravaUmaRevisaoDeCriacaoComOAutor() {
        cenario.reservar(api, ana, carlos.id(), AS_8H);

        assertThat(jdbc.queryForObject("SELECT revtype FROM agendamento_aud", Short.class)).isZero();
        var autor = jdbc.queryForMap("SELECT usuario_id, papel, perfil_id FROM revinfo");
        assertThat(autor).containsEntry("papel", "ADMIN").containsEntry("perfil_id", null);
        assertThat(autor.get("usuario_id")).isNotNull();
    }

    @Test
    void profissionalGravaOPerfil() {
        var daAna = comoProfissional(ana);

        cenario.encaixar(apiComo(daAna), ana, AS_8H, "Seu Zé");

        var autor = jdbc.queryForMap("SELECT usuario_id, papel, perfil_id FROM revinfo");
        assertThat(autor)
                .containsEntry("usuario_id", daAna.id())
                .containsEntry("papel", "PROFISSIONAL")
                .containsEntry("perfil_id", ana);
    }

    @Test
    void mudarSoOLembreteNaoCriaRevisao() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);
        int antes = revisoes();

        transacao.executeWithoutResult(status ->
                em.find(Agendamento.class, id).programarLembrete(Instant.parse("2026-10-21T09:00:00Z")));

        assertThat(revisoes()).isEqualTo(antes);
    }

    @Test
    void semUsuarioOAutorFicaNulo() {
        UUID id = cenario.reservar(api, ana, carlos.id(), AS_8H);

        transacao.executeWithoutResult(status ->
                em.find(Agendamento.class, id).cancelar("imprevisto", relogio.instant(), Antecedencia.LIVRE));

        var autor = jdbc.queryForMap("SELECT usuario_id, papel, perfil_id FROM revinfo ORDER BY rev DESC LIMIT 1");
        assertThat(autor)
                .containsEntry("usuario_id", null)
                .containsEntry("papel", null)
                .containsEntry("perfil_id", null);
    }

    private int revisoes() {
        return jdbc.queryForObject("SELECT count(*) FROM agendamento_aud", Integer.class);
    }
}
