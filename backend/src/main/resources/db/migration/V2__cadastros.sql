-- Cadastros básicos: serviços oferecidos, profissionais (e quais serviços cada um atende) e clientes.

CREATE TABLE servico (
    id              uuid           PRIMARY KEY,
    nome            varchar(100)   NOT NULL,
    descricao       varchar(500),
    duracao_minutos integer        NOT NULL,
    preco           numeric(10, 2) NOT NULL,
    ativo           boolean        NOT NULL DEFAULT true,
    versao          bigint         NOT NULL,
    criado_em       timestamptz    NOT NULL,
    atualizado_em   timestamptz    NOT NULL,
    CONSTRAINT ck_servico_duracao CHECK (duracao_minutos BETWEEN 5 AND 480),
    CONSTRAINT ck_servico_preco CHECK (preco >= 0)
);

-- "Corte" e "corte" são o mesmo serviço.
CREATE UNIQUE INDEX uk_servico_nome ON servico (lower(nome));

CREATE TABLE profissional (
    id            uuid         PRIMARY KEY,
    nome          varchar(120) NOT NULL,
    email         varchar(255) NOT NULL,
    telefone      varchar(20),
    ativo         boolean      NOT NULL DEFAULT true,
    versao        bigint       NOT NULL,
    criado_em     timestamptz  NOT NULL,
    atualizado_em timestamptz  NOT NULL,
    CONSTRAINT uk_profissional_email UNIQUE (email)
);

CREATE TABLE profissional_servico (
    profissional_id uuid NOT NULL REFERENCES profissional (id),
    servico_id      uuid NOT NULL REFERENCES servico (id),
    PRIMARY KEY (profissional_id, servico_id)
);

-- A chave primária já cobre buscas por profissional; este índice cobre "quem atende este serviço".
CREATE INDEX ix_profissional_servico_servico ON profissional_servico (servico_id);

CREATE TABLE cliente (
    id            uuid         PRIMARY KEY,
    nome          varchar(120) NOT NULL,
    email         varchar(255) NOT NULL,
    telefone      varchar(20),
    versao        bigint       NOT NULL,
    criado_em     timestamptz  NOT NULL,
    atualizado_em timestamptz  NOT NULL,
    CONSTRAINT uk_cliente_email UNIQUE (email)
);
