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

/** Versão curta do serviço, dentro de outras respostas. */
export type ServicoResumo = Pick<Servico, 'id' | 'nome' | 'duracaoMinutos' | 'preco'>;

export interface Profissional {
  readonly id: string;
  readonly nome: string;
  readonly ativo: boolean;
}

/** Detalhe do profissional, com os serviços que ele atende. */
export interface ProfissionalComServicos extends Profissional {
  readonly servicos: readonly ServicoResumo[];
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
  readonly canceladoEm: string | null;
  readonly motivoCancelamento: string | null;
  readonly profissional: Profissional;
  readonly servico: ServicoResumo;
  readonly cliente: {
    readonly id: string;
    readonly nome: string;
    readonly telefone: string | null;
  };
}

export interface NovoAgendamento {
  readonly profissionalId: string;
  readonly servicoId: string;
  readonly clienteId: string;
  readonly inicio: string;
}

/** Quem chegou sem reserva, encaixado pelo profissional. */
export interface NovoEncaixe {
  readonly profissionalId: string;
  readonly servicoId: string;
  readonly inicio: string;
  readonly nomeDoCliente: string;
  readonly telefone: string | null;
}

/** Período em que o profissional não atende (folga, consulta, almoço fora de hora). */
export interface Bloqueio {
  readonly id: string;
  readonly inicio: string;
  readonly fim: string;
  readonly motivo: string | null;
}

export type NovoBloqueio = Omit<Bloqueio, 'id'>;

export type TipoDeNotificacao = 'CRIADO' | 'REMARCADO' | 'CANCELADO';

/** Aviso do sino do barbeiro; o texto já vem pronto do back-end. */
export interface Notificacao {
  readonly id: string;
  readonly tipo: TipoDeNotificacao;
  readonly texto: string;
  /** Horário da reserva, para abrir a Agenda no dia certo. */
  readonly inicio: string;
  readonly criadaEm: string;
  readonly lida: boolean;
}

export interface NotificacoesResposta {
  readonly naoLidas: number;
  readonly recentes: readonly Notificacao[];
}

/** Problem Details (RFC 9457) devolvido pelo TratadorDeErros. */
export interface Problema {
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly erros?: readonly { readonly campo: string; readonly mensagem: string }[];
}
