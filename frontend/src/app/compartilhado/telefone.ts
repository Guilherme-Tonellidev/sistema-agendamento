import { Pipe, PipeTransform } from '@angular/core';
import { SchemaPath, validate } from '@angular/forms/signals';

/** Aceita o número do jeito que a pessoa digitar: "(14) 99888-7777" vira 14998887777. */
export const soDigitos = (texto: string) => texto.replace(/\D/g, '');

/** Celular opcional, como o back-end aceita: DDD e número, 10 ou 11 dígitos. */
export function telefoneOpcional(campo: SchemaPath<string>): void {
  validate(campo, ({ value }) =>
    /^(\d{10,11})?$/.test(soDigitos(value()))
      ? undefined
      : { kind: 'telefone', message: 'Informe o DDD e o número.' },
  );
}

/** Conversa no WhatsApp com o número (com DDD, só dígitos), já com a mensagem escrita. */
export function linkDoWhatsApp(telefone: string, mensagem: string): string {
  return `https://wa.me/55${telefone}?text=${encodeURIComponent(mensagem)}`;
}

/** 14998123344 vira "(14) 99812-3344"; 1438123344, "(14) 3812-3344". */
@Pipe({ name: 'telefone' })
export class TelefonePipe implements PipeTransform {
  transform(digitos: string): string {
    const numero = digitos.slice(2);
    return `(${digitos.slice(0, 2)}) ${numero.slice(0, -4)}-${numero.slice(-4)}`;
  }
}
