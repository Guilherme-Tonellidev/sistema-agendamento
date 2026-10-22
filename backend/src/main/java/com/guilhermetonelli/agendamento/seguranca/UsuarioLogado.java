package com.guilhermetonelli.agendamento.seguranca;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quem fez a requisição, lido do token de acesso. É o principal da autenticação, então os controllers
 * recebem com {@code @AuthenticationPrincipal UsuarioLogado eu}.
 */
public record UsuarioLogado(UUID id, Papel papel, UUID profissionalId, UUID clienteId) {

    static final String CLAIM_PAPEL = "papel";
    static final String CLAIM_PROFISSIONAL = "profissional_id";
    static final String CLAIM_CLIENTE = "cliente_id";

    public UsuarioLogado {
        Objects.requireNonNull(id);
        Objects.requireNonNull(papel);
    }

    static UsuarioLogado de(Jwt jwt) {
        return new UsuarioLogado(
                UUID.fromString(jwt.getSubject()),
                Papel.valueOf(jwt.getClaimAsString(CLAIM_PAPEL)),
                uuid(jwt.getClaimAsString(CLAIM_PROFISSIONAL)),
                uuid(jwt.getClaimAsString(CLAIM_CLIENTE)));
    }

    public boolean admin() {
        return papel == Papel.ADMIN;
    }

    public boolean cliente() {
        return papel == Papel.CLIENTE;
    }

    public boolean ehProfissional(UUID id) {
        return papel == Papel.PROFISSIONAL && id.equals(profissionalId);
    }

    public boolean ehCliente(UUID id) {
        return papel == Papel.CLIENTE && id.equals(clienteId);
    }

    /** Admin, o profissional ou o cliente envolvidos. */
    public boolean participaDe(UUID profissionalId, UUID clienteId) {
        return admin() || ehProfissional(profissionalId) || ehCliente(clienteId);
    }

    public void garantirAgendaDe(UUID profissionalId) {
        if (!admin() && !ehProfissional(profissionalId)) {
            throw new AccessDeniedException("Só o próprio profissional ou um administrador gerencia esta agenda.");
        }
    }

    public void garantirCliente(UUID clienteId) {
        if (!admin() && !ehCliente(clienteId)) {
            throw new AccessDeniedException("Você só pode acessar o seu próprio cadastro.");
        }
    }

    private static UUID uuid(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }
}
