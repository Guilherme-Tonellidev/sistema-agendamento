import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { DuracaoPipe, PrecoPipe } from './formatos';

describe('DuracaoPipe', () => {
  it('escreve a duração como na tabela da barbearia', () => {
    const duracao = new DuracaoPipe();

    expect([15, 45, 60, 90].map((minutos) => duracao.transform(minutos))).toEqual([
      '15 min',
      '45 min',
      '1h',
      '1h30',
    ]);
  });
});

describe('PrecoPipe', () => {
  it('só mostra centavos quando o preço não é redondo', () => {
    registerLocaleData(localePt);
    TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: 'pt-BR' }] });
    const preco = TestBed.runInInjectionContext(() => new PrecoPipe());

    // O Intl separa "R$" do número com um espaço que não quebra a linha.
    expect(preco.transform(45).replace(/\s/g, ' ')).toBe('R$ 45');
    expect(preco.transform(37.5).replace(/\s/g, ' ')).toBe('R$ 37,50');
  });
});
