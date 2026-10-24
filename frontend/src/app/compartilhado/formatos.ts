import { formatCurrency } from '@angular/common';
import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';

/** Duração como na tabela da barbearia: 30 vira "30 min"; 60, "1h"; 90, "1h30". */
export function duracao(minutos: number): string {
  if (minutos < 60) {
    return `${minutos} min`;
  }
  const resto = minutos % 60;
  return `${Math.floor(minutos / 60)}h${resto ? String(resto).padStart(2, '0') : ''}`;
}

@Pipe({ name: 'duracao' })
export class DuracaoPipe implements PipeTransform {
  transform(minutos: number): string {
    return duracao(minutos);
  }
}

/** Preço de tabela: sem centavos quando é redondo ("R$ 45"), com eles quando não ("R$ 37,50"). */
@Pipe({ name: 'preco' })
export class PrecoPipe implements PipeTransform {
  private readonly idioma = inject(LOCALE_ID);

  transform(valor: number): string {
    const casas = Number.isInteger(valor) ? '1.0-0' : '1.2-2';
    return formatCurrency(valor, this.idioma, 'R$', 'BRL', casas);
  }
}
