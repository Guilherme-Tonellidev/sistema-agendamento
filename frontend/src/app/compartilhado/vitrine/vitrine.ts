import { Component, DestroyRef, computed, inject, signal } from '@angular/core';

import { FUNCIONAMENTO, abertaEm, expedienteDo } from '../../core/barbearia';
import { dataNoFuso } from '../../core/datas';

/** A fachada da barbearia: poste, letreiro no vidro, horário e o neon de aberto ou fechado. */
@Component({
  selector: 'app-vitrine',
  templateUrl: './vitrine.html',
  styleUrl: './vitrine.css',
})
export class Vitrine {
  private readonly agora = signal(new Date());
  private readonly expedienteDeHoje = computed(() => expedienteDo(dataNoFuso(this.agora())));

  protected readonly horarios = computed(() =>
    FUNCIONAMENTO.map((expediente) => ({
      rotulo: expediente.rotulo,
      valor: expediente.abre === null ? 'fechado' : `${expediente.abre}h às ${expediente.fecha}h`,
      hoje: expediente === this.expedienteDeHoje(),
    })),
  );

  protected readonly aberto = computed(() => abertaEm(this.agora()));

  constructor() {
    const relogio = setInterval(() => this.agora.set(new Date()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(relogio));
  }
}
