-- Contas de acesso e sessões de refresh token.

CREATE TABLE usuario (
    id              uuid         PRIMARY KEY,
    email           varchar(255) NOT NULL,
    -- Nulo para contas que só entram com o Google.
    senha_hash      varchar(255),
    papel           varchar(20)  NOT NULL,
    ativo           boolean      NOT NULL DEFAULT true,
    profissional_id uuid         UNIQUE REFERENCES profissional (id),
    cliente_id      uuid         UNIQUE REFERENCES cliente (id),
    versao          bigint       NOT NULL,
    criado_em       timestamptz  NOT NULL,
    atualizado_em   timestamptz  NOT NULL,
    CONSTRAINT uk_usuario_email UNIQUE (email),
    -- Cada papel aponta exatamente para o cadastro que lhe corresponde.
    CONSTRAINT ck_usuario_papel CHECK (
        (papel = 'ADMIN' AND profissional_id IS NULL AND cliente_id IS NULL)
        OR (papel = 'PROFISSIONAL' AND profissional_id IS NOT NULL AND cliente_id IS NULL)
        OR (papel = 'CLIENTE' AND cliente_id IS NOT NULL AND profissional_id IS NULL))
);

-- Guarda só o hash SHA-256 do refresh token: um vazamento do banco não entrega sessões válidas.
-- Tokens renovados ficam na mesma família; reutilizar um token já trocado derruba a família inteira.
CREATE TABLE sessao (
    id          uuid        PRIMARY KEY,
    usuario_id  uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    familia     uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expira_em   timestamptz NOT NULL,
    usada_em    timestamptz,
    revogada_em timestamptz,
    criado_em   timestamptz NOT NULL,
    CONSTRAINT uk_sessao_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_sessao_familia ON sessao (familia);
CREATE INDEX ix_sessao_usuario ON sessao (usuario_id);
