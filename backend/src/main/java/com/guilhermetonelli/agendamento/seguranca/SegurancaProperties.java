package com.guilhermetonelli.agendamento.seguranca;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuração de segurança ({@code seguranca.*}).
 *
 * @param jwtSegredo chave HMAC-SHA256 dos tokens de acesso, em Base64, com pelo menos 256 bits
 * @param validadeDoToken vida curta do token de acesso: um token vazado vale por pouco tempo
 * @param validadeDaSessao por quanto tempo o refresh token permite renovar sem novo login
 * @param cookieSeguro marca o cookie de sessão como {@code Secure} (só trafega em HTTPS)
 * @param origensPermitidas origens do front-end liberadas no CORS
 * @param adminEmail e {@code adminSenha}: administrador criado na inicialização, se ainda não existir
 */
@ConfigurationProperties("seguranca")
public record SegurancaProperties(
        String jwtSegredo,
        @DefaultValue("agendamento-api") String jwtEmissor,
        @DefaultValue("PT15M") Duration validadeDoToken,
        @DefaultValue("P14D") Duration validadeDaSessao,
        @DefaultValue("true") boolean cookieSeguro,
        @DefaultValue("http://localhost:4200") List<String> origensPermitidas,
        String adminEmail,
        String adminSenha) {

    private static final int BYTES_MINIMOS = 32;

    public SegurancaProperties {
        if (!StringUtils.hasText(jwtSegredo) || Base64.getDecoder().decode(jwtSegredo).length < BYTES_MINIMOS) {
            throw new IllegalArgumentException(
                    "seguranca.jwt-segredo deve ter pelo menos 256 bits em Base64 (gere com: openssl rand -base64 32)");
        }
    }

    SecretKey chaveJwt() {
        return new SecretKeySpec(Base64.getDecoder().decode(jwtSegredo), "HmacSHA256");
    }

    boolean temAdminInicial() {
        return StringUtils.hasText(adminEmail) && StringUtils.hasText(adminSenha);
    }
}
