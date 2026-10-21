-- Disponibilidade: jornada semanal (horários locais do negócio) e bloqueios pontuais (folgas, férias).

CREATE TABLE jornada (
    id              uuid     PRIMARY KEY,
    profissional_id uuid     NOT NULL REFERENCES profissional (id),
    -- ISO 8601: 1 = segunda-feira ... 7 = domingo.
    dia_semana      smallint NOT NULL,
    inicio          time     NOT NULL,
    fim             time     NOT NULL,
    CONSTRAINT ck_jornada_dia_semana CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_jornada_horario CHECK (inicio < fim)
);

CREATE INDEX ix_jornada_profissional ON jornada (profissional_id, dia_semana);

CREATE TABLE bloqueio (
    id              uuid         PRIMARY KEY,
    profissional_id uuid         NOT NULL REFERENCES profissional (id),
    inicio          timestamptz  NOT NULL,
    fim             timestamptz  NOT NULL,
    motivo          varchar(200),
    versao          bigint       NOT NULL,
    criado_em       timestamptz  NOT NULL,
    atualizado_em   timestamptz  NOT NULL,
    CONSTRAINT ck_bloqueio_periodo CHECK (inicio < fim)
);

CREATE INDEX ix_bloqueio_profissional ON bloqueio (profissional_id, inicio);
