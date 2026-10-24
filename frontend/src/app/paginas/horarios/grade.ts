import { minutosNoFuso } from '../../core/datas';

export interface Periodo<T> {
  readonly nome: string;
  readonly horarios: readonly T[];
}

/** Limite superior de cada período, em minutos desde a meia-noite. */
const PERIODOS = [
  { nome: 'Manhã', ate: 12 * 60 },
  { nome: 'Tarde', ate: 18 * 60 },
  { nome: 'Noite', ate: 24 * 60 },
];

/** Separa os horários em manhã, tarde e noite, pela hora local da barbearia. */
export function agruparPorPeriodo<T extends { readonly inicio: string }>(
  horarios: readonly T[],
): Periodo<T>[] {
  return PERIODOS.map(({ nome, ate }, indice) => {
    const de = indice === 0 ? 0 : PERIODOS[indice - 1].ate;
    return {
      nome,
      horarios: horarios.filter((horario) => {
        const minutos = minutosNoFuso(horario.inicio);
        return minutos >= de && minutos < ate;
      }),
    };
  });
}

/**
 * Junta as agendas de vários barbeiros ("sem preferência"): cada horário aparece uma vez, com o
 * primeiro barbeiro da lista que estiver livre nele.
 */
export function primeiroLivrePorHorario<T extends { readonly inicio: string }>(
  agendas: readonly (readonly T[])[],
): T[] {
  const porInicio = new Map<string, T>();
  for (const horario of agendas.flat()) {
    if (!porInicio.has(horario.inicio)) {
      porInicio.set(horario.inicio, horario);
    }
  }
  return [...porInicio.values()].sort((a, b) => a.inicio.localeCompare(b.inicio));
}
