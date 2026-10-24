import { Agendamento, Bloqueio, HorarioLivre } from '../../core/api/modelos';

/** Trecho livre da agenda em que cabe pelo menos o serviço mais curto. */
export interface Vao {
  readonly inicio: string;
  readonly fim: string;
}

export type ItemDaFila =
  | { readonly tipo: 'atendimento'; readonly inicio: string; readonly agendamento: Agendamento }
  | { readonly tipo: 'vao'; readonly inicio: string; readonly vao: Vao }
  | { readonly tipo: 'bloqueio'; readonly inicio: string; readonly bloqueio: Bloqueio };

export interface Fila {
  /** Já começaram e ainda não foram encerrados pelo barbeiro. */
  readonly agora: readonly Agendamento[];
  /** Atendimentos por começar, vãos livres e bloqueios que ainda não acabaram. */
  readonly proximos: readonly ItemDaFila[];
  /** Concluídos e faltas. */
  readonly encerrados: readonly Agendamento[];
}

const instante = (texto: string) => Date.parse(texto);
const porInicio = (a: { inicio: string }, b: { inicio: string }) =>
  instante(a.inicio) - instante(b.inicio);

/**
 * Junta os horários livres do serviço mais curto: 15:15–15:30 e 15:30–15:45 viram o vão 15:15–15:45.
 * Como vêm da API, os vãos já respeitam jornada, bloqueios, agendamentos e a hora atual.
 */
export function juntarEmVaos(livres: readonly HorarioLivre[]): Vao[] {
  const vaos: Vao[] = [];
  for (const { inicio, fim } of [...livres].sort(porInicio)) {
    const anterior = vaos.at(-1);
    if (anterior && instante(inicio) <= instante(anterior.fim)) {
      vaos[vaos.length - 1] = {
        inicio: anterior.inicio,
        fim: instante(fim) > instante(anterior.fim) ? fim : anterior.fim,
      };
    } else {
      vaos.push({ inicio, fim });
    }
  }
  return vaos;
}

/** Em andamento até o fim do horário; depois disso, pendente até o barbeiro encerrar. */
export type Estado = 'confirmado' | 'andamento' | 'pendente' | 'concluido' | 'faltou' | 'cancelado';

export function estadoDe({ status, inicio, fim }: Agendamento, agora: Date): Estado {
  switch (status) {
    case 'CONCLUIDO':
      return 'concluido';
    case 'NAO_COMPARECEU':
      return 'faltou';
    case 'CANCELADO':
      return 'cancelado';
    case 'CONFIRMADO':
      if (instante(inicio) > agora.getTime()) {
        return 'confirmado';
      }
      return instante(fim) > agora.getTime() ? 'andamento' : 'pendente';
  }
}

/** O dia do barbeiro em três partes; cancelados saem da fila, o horário deles já está livre. */
export function montarFila(
  agendamentos: readonly Agendamento[],
  bloqueios: readonly Bloqueio[],
  vaos: readonly Vao[],
  agora: Date,
): Fila {
  const ainda = (fim: string) => instante(fim) > agora.getTime();
  const comecou = ({ inicio }: Agendamento) => instante(inicio) <= agora.getTime();
  const confirmados = agendamentos.filter(({ status }) => status === 'CONFIRMADO');
  return {
    agora: confirmados.filter(comecou).sort(porInicio),
    proximos: [
      ...confirmados
        .filter((agendamento) => !comecou(agendamento))
        .map((agendamento) => ({
          tipo: 'atendimento' as const,
          inicio: agendamento.inicio,
          agendamento,
        })),
      ...vaos
        .filter(({ fim }) => ainda(fim))
        .map((vao) => ({ tipo: 'vao' as const, inicio: vao.inicio, vao })),
      ...bloqueios
        .filter(({ fim }) => ainda(fim))
        .map((bloqueio) => ({ tipo: 'bloqueio' as const, inicio: bloqueio.inicio, bloqueio })),
    ].sort(porInicio),
    encerrados: agendamentos
      .filter(({ status }) => status === 'CONCLUIDO' || status === 'NAO_COMPARECEU')
      .sort(porInicio),
  };
}

/** Faltas contam no total, mas não no previsto: esse dinheiro não entra. */
export function resumirDia(agendamentos: readonly Agendamento[]) {
  const doDia = agendamentos.filter(({ status }) => status !== 'CANCELADO');
  return {
    concluidos: doDia.filter(({ status }) => status === 'CONCLUIDO').length,
    total: doDia.length,
    previsto: doDia
      .filter(({ status }) => status !== 'NAO_COMPARECEU')
      .reduce((soma, { preco }) => soma + preco, 0),
  };
}
