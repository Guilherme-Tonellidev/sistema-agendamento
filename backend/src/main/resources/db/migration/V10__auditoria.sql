-- Histórico dos agendamentos (Hibernate Envers). Cada mudança num agendamento grava uma revisão em revinfo, com o autor,
-- e a foto da linha em agendamento_aud. Só o agendamento é auditado: profissional, serviço e cliente entram só pelo id.
-- Agendamentos criados antes desta migração não têm revisão de criação.

CREATE TABLE revinfo (
    rev        bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revtstmp   bigint      NOT NULL,
    -- O autor vem do token. Sem chave estrangeira: o token pode trazer um usuário que já não existe.
    -- Tudo nulo quando não há usuário (job do Quartz, testes).
    usuario_id uuid,
    papel      varchar(20),
    -- profissional_id ou cliente_id do autor; nulo para o administrador.
    perfil_id  uuid
);

CREATE TABLE agendamento_aud (
    id                  uuid           NOT NULL,
    rev                 bigint         NOT NULL REFERENCES revinfo (rev),
    -- 0 criação, 1 alteração, 2 remoção.
    revtype             smallint,
    profissional_id     uuid,
    servico_id          uuid,
    cliente_id          uuid,
    inicio              timestamptz,
    fim                 timestamptz,
    preco               numeric(10, 2),
    status              varchar(20),
    cancelado_em        timestamptz,
    motivo_cancelamento varchar(200),
    PRIMARY KEY (id, rev)
);
