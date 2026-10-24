/** Contratos da API do back-end (pacote dto de cada módulo). */

export type Papel = 'ADMIN' | 'PROFISSIONAL' | 'CLIENTE';

export interface Usuario {
  readonly id: string;
  readonly email: string;
  readonly papel: Papel;
  readonly profissionalId: string | null;
  readonly clienteId: string | null;
}

export interface Token {
  readonly tokenDeAcesso: string;
  readonly tipo: string;
  readonly expiraEmSegundos: number;
}

export interface Credenciais {
  readonly email: string;
  readonly senha: string;
}

export interface Cadastro extends Credenciais {
  readonly nome: string;
  readonly telefone: string | null;
}

/** Formato do PagedModel do Spring Data. */
export interface Pagina<T> {
  readonly content: readonly T[];
  readonly page: {
    readonly size: number;
    readonly number: number;
    readonly totalElements: number;
    readonly totalPages: number;
  };
}

export interface Servico {
  readonly id: string;
  readonly nome: string;
  readonly descricao: string | null;
  readonly duracaoMinutos: number;
  readonly preco: number;
  readonly ativo: boolean;
}

export interface Profissional {
  readonly id: string;
  readonly nome: string;
  readonly ativo: boolean;
}

export interface HorarioLivre {
  readonly inicio: string;
  readonly fim: string;
}

export type StatusAgendamento = 'CONFIRMADO' | 'CANCELADO' | 'CONCLUIDO' | 'NAO_COMPARECEU';

export interface Agendamento {
  readonly id: string;
  readonly inicio: string;
  readonly fim: string;
  readonly preco: number;
  readonly status: StatusAgendamento;
  readonly profissional: Profissional;
  readonly servico: Pick<Servico, 'id' | 'nome' | 'duracaoMinutos' | 'preco'>;
}

export interface NovoAgendamento {
  readonly profissionalId: string;
  readonly servicoId: string;
  readonly clienteId: string;
  readonly inicio: string;
}

/** Problem Details (RFC 9457) devolvido pelo TratadorDeErros. */
export interface Problema {
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly erros?: readonly { readonly campo: string; readonly mensagem: string }[];
}
