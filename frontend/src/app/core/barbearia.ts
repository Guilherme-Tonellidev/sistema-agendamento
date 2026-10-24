import { dataNoFuso, diaDaSemana, minutosNoFuso } from './datas';

/** Horário de funcionamento, em horas cheias. A jornada de cada barbeiro fica dentro dele. */
export interface Expediente {
  readonly rotulo: string;
  readonly abre: number | null;
  readonly fecha: number | null;
}

const DIAS_UTEIS: Expediente = { rotulo: 'Seg a sex', abre: 9, fecha: 20 };
const SABADO: Expediente = { rotulo: 'Sábado', abre: 8, fecha: 18 };
const DOMINGO: Expediente = { rotulo: 'Domingo', abre: null, fecha: null };

/** Na ordem em que aparecem na vitrine. */
export const FUNCIONAMENTO: readonly Expediente[] = [DIAS_UTEIS, SABADO, DOMINGO];

/** Índice igual ao de Date#getDay: 0 é domingo. */
const POR_DIA_DA_SEMANA: readonly Expediente[] = [
  DOMINGO,
  DIAS_UTEIS,
  DIAS_UTEIS,
  DIAS_UTEIS,
  DIAS_UTEIS,
  DIAS_UTEIS,
  SABADO,
];

/** O expediente do dia (AAAA-MM-DD). */
export function expedienteDo(data: string): Expediente {
  return POR_DIA_DA_SEMANA[diaDaSemana(data)];
}

/** Se a casa está de portas abertas nesse instante. */
export function abertaEm(instante: Date): boolean {
  const { abre, fecha } = expedienteDo(dataNoFuso(instante));
  const minutos = minutosNoFuso(instante);
  return abre !== null && fecha !== null && minutos >= abre * 60 && minutos < fecha * 60;
}

/** Se ainda dá para marcar nesse dia: a casa abre nele e, sendo hoje, ainda não fechou. */
export function atendeEm(data: string, agora: Date): boolean {
  const { fecha } = expedienteDo(data);
  return fecha !== null && (data !== dataNoFuso(agora) || minutosNoFuso(agora) < fecha * 60);
}
