package com.guilhermetonelli.agendamento.agendador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.guilhermetonelli.agendamento.Cenario;
import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.notificacao.NotificacaoService;
import com.guilhermetonelli.agendamento.seguranca.AutenticacaoService;

/** A limpeza diária apaga só o que já não serve para nada e está registrada no Quartz. */
class LimpezaTest extends IntegracaoTest {

    @Autowired
    private AutenticacaoService autenticacao;

    @Autowired
    private NotificacaoService notificacoes;

    @Test
    void gatilhoDiarioEstaGravadoNoBanco() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qrtz_cron_triggers WHERE trigger_name = 'limpeza'", Integer.class)).isEqualTo(1);
    }

    @Test
    void apagaSessoesVencidasHaMaisDeUmDiaEGuardaAsOutras() {
        UUID usuario = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, email, papel, versao, criado_em, atualizado_em) VALUES (?, 'a@b.com', 'ADMIN', 0, ?, ?)",
                usuario, antes(Duration.ZERO), antes(Duration.ZERO));
        sessao(usuario, "a", Duration.ofDays(2).negated());
        sessao(usuario, "b", Duration.ofHours(12).negated());
        sessao(usuario, "c", Duration.ofDays(7));

        assertThat(autenticacao.apagarSessoesVencidas()).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT token_hash FROM sessao ORDER BY token_hash", String.class))
                .containsExactly("b".repeat(64), "c".repeat(64));
    }

    @Test
    void apagaNotificacoesComMaisDeNoventaDiasEGuardaAsOutras() {
        UUID barbeiro = new Cenario(api).barbeiro("Rafael Souza");
        notificacao(barbeiro, "velha", Duration.ofDays(91));
        notificacao(barbeiro, "recente", Duration.ofDays(89));

        assertThat(notificacoes.apagarAntigas()).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT texto FROM notificacao", String.class)).containsExactly("recente");
    }

    private void sessao(UUID usuario, String letra, Duration expiraEmRelacaoAAgora) {
        jdbc.update("INSERT INTO sessao (id, usuario_id, familia, token_hash, expira_em, criado_em) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), usuario, UUID.randomUUID(), letra.repeat(64), depois(expiraEmRelacaoAAgora), antes(Duration.ZERO));
    }

    private void notificacao(UUID barbeiro, String texto, Duration idade) {
        jdbc.update("INSERT INTO notificacao (id, profissional_id, tipo, texto, inicio, versao, criado_em, atualizado_em) VALUES (?, ?, 'CRIADO', ?, ?, 0, ?, ?)",
                UUID.randomUUID(), barbeiro, texto, antes(Duration.ZERO), antes(idade), antes(idade));
    }

    private OffsetDateTime antes(Duration idade) {
        return depois(idade.negated());
    }

    private OffsetDateTime depois(Duration distancia) {
        Instant instante = relogio.instant().plus(distancia);
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
