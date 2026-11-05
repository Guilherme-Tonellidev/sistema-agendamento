package com.guilhermetonelli.agendamento.auditoria;

import java.util.UUID;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.guilhermetonelli.agendamento.seguranca.Papel;
import com.guilhermetonelli.agendamento.seguranca.UsuarioLogado;

/**
 * Preenche o autor de cada revisão com o usuário do token. Fora de uma requisição (job do Quartz, testes) não há
 * usuário e o autor fica nulo. Não consulta o banco: roda no meio do commit.
 */
public class AutorDaRevisao implements RevisionListener {

    @Override
    public void newRevision(Object revisao) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioLogado usuario) {
            UUID perfil = usuario.papel() == Papel.PROFISSIONAL ? usuario.profissionalId() : usuario.clienteId();
            ((RevisaoDeAuditoria) revisao).definirAutor(usuario.id(), usuario.papel().name(), perfil);
        }
    }
}
