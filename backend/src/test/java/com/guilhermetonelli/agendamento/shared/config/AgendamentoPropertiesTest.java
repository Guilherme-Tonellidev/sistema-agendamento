package com.guilhermetonelli.agendamento.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.guilhermetonelli.agendamento.shared.config.AgendamentoProperties.Lembrete;

class AgendamentoPropertiesTest {

    private static final Duration UM_DIA = Duration.ofHours(24);

    @Test
    void padroesDoLembreteSao24HEUmaHora() {
        var propriedades = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("agendamento", AgendamentoProperties.class);

        assertThat(propriedades.lembrete().antecedencia()).isEqualTo(UM_DIA);
        assertThat(propriedades.lembrete().antecedenciaReservaTardia()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void antecedenciaNaoPositivaEhRecusada() {
        assertThatThrownBy(() -> new Lembrete(Duration.ZERO, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agendamento.lembrete.antecedencia deve ser positiva");
    }

    @Test
    void reservaTardiaNaoPositivaOuNaoMenorQueANormalEhRecusada() {
        String mensagem = "agendamento.lembrete.antecedencia-reserva-tardia deve ser positiva e menor que agendamento.lembrete.antecedencia";

        assertThatThrownBy(() -> new Lembrete(UM_DIA, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(mensagem);
        assertThatThrownBy(() -> new Lembrete(UM_DIA, UM_DIA))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(mensagem);
    }
}
