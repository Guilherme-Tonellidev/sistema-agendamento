package com.guilhermetonelli.agendamento.seguranca;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Garante um administrador na primeira subida, a partir de ADMIN_EMAIL e ADMIN_SENHA. */
@Component
class CriadorDeAdministrador implements ApplicationRunner {

    private final AutenticacaoService service;
    private final SegurancaProperties properties;

    CriadorDeAdministrador(AutenticacaoService service, SegurancaProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.temAdminInicial()) {
            service.criarAdminSeNaoExistir(properties.adminEmail(), properties.adminSenha());
        }
    }
}
