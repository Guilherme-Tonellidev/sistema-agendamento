/*
 * Datas da agenda são sempre do fuso da barbearia, não do navegador: quem abre o site de outro
 * fuso continua vendo os horários da barbearia. Dias trafegam como texto AAAA-MM-DD.
 */

/** Fuso em que a barbearia trabalha; o back-end interpreta as datas da API no mesmo fuso. */
export const FUSO = 'America/Sao_Paulo';

const DIAS_CURTOS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

const formatoData = new Intl.DateTimeFormat('en-CA', {
  timeZone: FUSO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const formatoHora = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const formatoExtenso = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'UTC',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

const meioDiaUtc = (data: string) => new Date(`${data}T12:00:00Z`);

/** Dia (AAAA-MM-DD) em que o instante cai no fuso da barbearia. */
export function dataNoFuso(instante: Date | string): string {
  return formatoData.format(new Date(instante));
}

/** Hora (HH:mm) do instante no fuso da barbearia. */
export function horaNoFuso(instante: Date | string): string {
  return formatoHora.format(new Date(instante));
}

/** Minutos desde a meia-noite, no fuso da barbearia. */
export function minutosNoFuso(instante: Date | string): number {
  const [horas, minutos] = horaNoFuso(instante).split(':').map(Number);
  return horas * 60 + minutos;
}

export function somarDias(data: string, dias: number): string {
  const dia = meioDiaUtc(data);
  dia.setUTCDate(dia.getUTCDate() + dias);
  return dia.toISOString().slice(0, 10);
}

/** 0 é domingo, como em Date#getDay. */
export function diaDaSemana(data: string): number {
  return meioDiaUtc(data).getUTCDay();
}

export function diaCurto(data: string): string {
  return DIAS_CURTOS[diaDaSemana(data)];
}

export function diaDoMes(data: string): number {
  return Number(data.slice(8, 10));
}

/** Ex.: "quarta, 21 de outubro". */
export function dataPorExtenso(data: string): string {
  return formatoExtenso.format(meioDiaUtc(data)).replace('-feira', '');
}
