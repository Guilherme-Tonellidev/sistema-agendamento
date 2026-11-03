package com.guilhermetonelli.agendamento.shared.redis;

import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.ClientOptions;

/**
 * Com a conexão caída, o Lettuce enfileira os comandos até reconectar. Aqui eles são recusados na hora: o login e a
 * consulta de horários seguem sem o Redis em vez de esperar por ele.
 */
@Configuration(proxyBeanMethods = false)
class RedisConfig {

    @Bean
    LettuceClientOptionsBuilderCustomizer recusarComandosSemConexao() {
        return opcoes -> opcoes.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
