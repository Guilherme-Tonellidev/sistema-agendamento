import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { Carregamento } from './core/carregamento/carregamento';

describe('App', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }),
  );

  it('mostra a barra listrada só enquanto há chamada em andamento', async () => {
    const fixture = TestBed.createComponent(App);
    const carregamento = TestBed.inject(Carregamento);
    const barra = () => fixture.nativeElement.querySelector('[role="progressbar"]');

    await fixture.whenStable();
    expect(barra()).toBeNull();

    carregamento.comecou();
    await fixture.whenStable();
    expect(barra()).not.toBeNull();

    carregamento.terminou();
    await fixture.whenStable();
    expect(barra()).toBeNull();
  });
});
