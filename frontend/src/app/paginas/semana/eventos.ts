import { Agendamento, Pagina, Profissional } from '../../core/api/modelos';
import { diaDaSemana, somarDias } from '../../core/datas';

/**
 * Uma cor por barbeiro, na ordem da equipe. Todas passam de 4,5 de contraste com o texto branco
 * (AA). Sem vermelho: a borda vermelha fica para quem faltou.
 */
export const PALETA: readonly string[] = [
  '#1d4ed8', // azul
  '#be185d', // rosa
  '#15803d', // verde
  '#7e22ce', // roxo
  '#c2410c', // laranja
  '#0e7490', // ciano
];

/** Um agendamento no formato que o FullCalendar lê como evento. */
export interface EventoDaGrade {
  readonly id: string;
  readonly title: string;
  readonly start: string;
  readonly end: string;
  readonly backgroundColor: string;
  readonly borderColor: string;
  readonly textColor: string;
  /** Lista mutável, que é o que o FullCalendar aceita. */
  readonly classNames: string[];
  readonly startEditable: boolean;
  readonly extendedProps: { readonly agendamento: Agendamento };
}

/** A cor do barbeiro pela posição na equipe; passada a paleta, ela recomeça. */
export function corDoBarbeiro(equipe: readonly Profissional[], id: string): string {
  const posicao = Math.max(
    equipe.findIndex((profissional) => profissional.id === id),
    0,
  );
  return PALETA[posicao % PALETA.length];
}

/** Segunda a domingo da semana do dia (AAAA-MM-DD). */
export function semanaDe(data: string): { de: string; ate: string } {
  const desdeSegunda = (diaDaSemana(data) + 6) % 7;
  const de = somarDias(data, -desdeSegunda);
  return { de, ate: somarDias(de, 6) };
}

/** Só o confirmado muda de horário; o estilo de cada status fica por conta da classe. */
export function paraEvento(
  agendamento: Agendamento,
  equipe: readonly Profissional[],
): EventoDaGrade {
  const cor = corDoBarbeiro(equipe, agendamento.profissional.id);
  return {
    id: agendamento.id,
    title: `${agendamento.cliente.nome} · ${agendamento.servico.nome}`,
    start: agendamento.inicio,
    end: agendamento.fim,
    backgroundColor: cor,
    borderColor: cor,
    textColor: '#ffffff',
    classNames: [`status-${agendamento.status.toLowerCase()}`],
    startEditable: agendamento.status === 'CONFIRMADO',
    extendedProps: { agendamento },
  };
}

/** Segue as páginas da API, uma de cada vez, até a última e devolve tudo junto. */
export async function todasAsPaginas<T>(
  buscar: (pagina: number) => Promise<Pagina<T>>,
): Promise<T[]> {
  const primeira = await buscar(0);
  const itens = [...primeira.content];
  for (let pagina = 1; pagina < primeira.page.totalPages; pagina++) {
    itens.push(...(await buscar(pagina)).content);
  }
  return itens;
}
