package com.guilhermetonelli.agendamento.seguranca;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.guilhermetonelli.agendamento.seguranca.dto.TokenResponse;

/** Emite os tokens de acesso (JWT assinado com HMAC-SHA256), validados pelo próprio resource server. */
@Component
public class EmissorDeTokens {

    private static final JwsHeader CABECALHO = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

    private final JwtEncoder encoder;
    private final SegurancaProperties properties;
    private final Clock clock;

    EmissorDeTokens(JwtEncoder encoder, SegurancaProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse emitir(UsuarioLogado usuario) {
        Instant agora = Instant.now(clock);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwtEmissor())
                .subject(usuario.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(properties.validadeDoToken()))
                .claim(UsuarioLogado.CLAIM_PAPEL, usuario.papel().name());
        if (usuario.profissionalId() != null) {
            claims.claim(UsuarioLogado.CLAIM_PROFISSIONAL, usuario.profissionalId().toString());
        }
        if (usuario.clienteId() != null) {
            claims.claim(UsuarioLogado.CLAIM_CLIENTE, usuario.clienteId().toString());
        }

        String token = encoder.encode(JwtEncoderParameters.from(CABECALHO, claims.build())).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.validadeDoToken().toSeconds());
    }
}
