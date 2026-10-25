package com.guilhermetonelli.agendamento.mensageria;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guilhermetonelli.agendamento.agendamento.evento.AgendamentoCriado;

import tools.jackson.databind.JavaType;

@Configuration
class MensageriaConfig {

    /**
     * A mensagem é o evento em JSON, com o tipo no cabeçalho. Só o pacote dos eventos é confiável: um
     * cabeçalho forjado não consegue fazer o consumidor instanciar uma classe qualquer. A recusa sai como erro
     * de conversão, que o Spring AMQP não repete: a mensagem vai direto para a fila de erros.
     */
    @Bean
    MessageConverter conversorJson() {
        DefaultJacksonJavaTypeMapper tipos = new DefaultJacksonJavaTypeMapper() {
            @Override
            public JavaType toJavaType(MessageProperties propriedades) {
                try {
                    return super.toJavaType(propriedades);
                } catch (IllegalArgumentException e) {
                    throw new MessageConversionException("Tipo recusado: " + e.getMessage(), e);
                }
            }
        };
        tipos.setTrustedPackages(AgendamentoCriado.class.getPackageName());
        JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter();
        conversor.setJavaTypeMapper(tipos);
        return conversor;
    }

    /** O handshake de uma conexão nova espera 10 s por padrão e não tem propriedade própria no Boot. */
    @Bean
    ConnectionFactoryCustomizer handshakeComLimiteDe2Segundos() {
        return cliente -> cliente.setHandshakeTimeout(2000);
    }

    /** Mensagem que não se lê hoje não se lerá em 18 s: vai para a fila de erros sem esgotar as tentativas. */
    @Bean
    RabbitListenerRetrySettingsCustomizer semRepeticaDeMensagemIlegivel() {
        return ajustes -> ajustes.getExceptionExcludes().add(MessageConversionException.class);
    }
}
