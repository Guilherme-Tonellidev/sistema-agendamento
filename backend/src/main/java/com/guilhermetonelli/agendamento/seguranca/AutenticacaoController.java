package com.guilhermetonelli.agendamento.seguranca;

import java.time.Duration;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guilhermetonelli.agendamento.seguranca.dto.AcessoRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.CadastroRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.LoginRequest;
import com.guilhermetonelli.agendamento.seguranca.dto.TokenResponse;
import com.guilhermetonelli.agendamento.seguranca.dto.UsuarioResponse;
import com.guilhermetonelli.agendamento.shared.exception.NaoAutenticadoException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * O token de acesso volta no corpo e o front guarda só em memória. O refresh token vai num cookie
 * HttpOnly (JavaScript não lê, então um XSS não o rouba), SameSite=Strict (outro site não o envia) e
 * restrito ao caminho {@code /api/auth}.
 */
@RestController
@Tag(name = "Autenticação", description = "Cadastro, login, renovação e logout")
class AutenticacaoController {

    static final String COOKIE = "refresh_token";
    private static final String CAMINHO_DO_COOKIE = "/api/auth";

    private final AutenticacaoService service;
    private final SegurancaProperties properties;

    AutenticacaoController(AutenticacaoService service, SegurancaProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/api/auth/cadastro")
    @Operation(summary = "Cria uma conta de cliente e já abre a sessão")
    ResponseEntity<TokenResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return comCookie(HttpStatus.CREATED, service.cadastrar(request));
    }

    @PostMapping("/api/auth/login")
    @Operation(summary = "Entra com e-mail e senha")
    ResponseEntity<TokenResponse> entrar(@Valid @RequestBody LoginRequest request) {
        return comCookie(HttpStatus.OK, service.entrar(request));
    }

    @PostMapping("/api/auth/refresh")
    @Operation(summary = "Troca o refresh token do cookie por um novo token de acesso")
    ResponseEntity<TokenResponse> renovar(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new NaoAutenticadoException("Sessão expirada ou inválida. Faça login novamente.");
        }
        return comCookie(HttpStatus.OK, service.renovar(refreshToken));
    }

    @PostMapping("/api/auth/logout")
    @Operation(summary = "Encerra a sessão deste dispositivo")
    ResponseEntity<Void> sair(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            service.sair(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }

    @GetMapping("/api/auth/eu")
    @Operation(summary = "Dados da conta logada")
    UsuarioResponse eu(@AuthenticationPrincipal UsuarioLogado eu) {
        return service.eu(eu);
    }

    @PutMapping("/api/profissionais/{id}/acesso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cria ou troca a senha de acesso de um profissional (login pelo e-mail dele)")
    void definirAcesso(@PathVariable UUID id, @Valid @RequestBody AcessoRequest request) {
        service.definirAcessoDeProfissional(id, request.senha());
    }

    private ResponseEntity<TokenResponse> comCookie(HttpStatus status, SessaoAberta sessao) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(sessao.refreshToken(), properties.validadeDaSessao()))
                .body(sessao.acesso());
    }

    private String cookie(String valor, Duration validade) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(properties.cookieSeguro())
                .sameSite("Strict")
                .path(CAMINHO_DO_COOKIE)
                .maxAge(validade)
                .build()
                .toString();
    }
}
