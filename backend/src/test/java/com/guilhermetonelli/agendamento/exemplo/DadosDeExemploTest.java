package com.guilhermetonelli.agendamento.exemplo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import com.guilhermetonelli.agendamento.IntegracaoTest;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;
import com.guilhermetonelli.agendamento.servico.dto.ServicoRequest;

/** Os testes usam o perfil local, que liga os dados de exemplo; cada teste começa com o banco limpo. */
class DadosDeExemploTest extends IntegracaoTest {

    @Autowired
    private DadosDeExemplo dadosDeExemplo;

    @Test
    void cadastraServicosBarbeirosEContasNoBancoSemServicos() {
        dadosDeExemplo.run(null);

        assertThat(jdbc.queryForList("SELECT nome FROM servico", String.class))
                .containsExactlyInAnyOrder("Corte", "Barba", "Corte + barba");
        assertThat(jdbc.queryForList("SELECT nome FROM profissional", String.class))
                .containsExactlyInAnyOrder("Rafael Souza", "Bruno Lima");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT profissional_id) FROM jornada", Integer.class)).isEqualTo(2);
        assertThat(entrar("rafael@tonelli.local", "barbeiro-local-123")).isEqualTo(HttpStatus.OK);
        assertThat(entrar("carlos@tonelli.local", "cliente-local-123")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void naoMexeNumBancoQueJaTemServicos() {
        api.post().uri("/api/servicos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServicoRequest("Pigmentação", null, 40, new BigDecimal("50.00")))
                .retrieve().toBodilessEntity();

        dadosDeExemplo.run(null);

        assertThat(jdbc.queryForList("SELECT nome FROM servico", String.class)).containsExactly("Pigmentação");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profissional", Integer.class)).isZero();
    }

    private HttpStatusCode entrar(String email, String senha) {
        return apiAnonima().post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, senha))
                .retrieve().toBodilessEntity().getStatusCode();
    }
}
