import { Component } from '@angular/core';

/** O ticket de papel com picote: a parte de cima vai em [topo], o resto no corpo. */
@Component({
  selector: 'app-ingresso',
  template: `
    <div class="topo"><ng-content select="[topo]" /></div>
    <div class="picote" aria-hidden="true"><span></span></div>
    <div class="corpo"><ng-content /></div>
  `,
  styleUrl: './ingresso.css',
})
export class Ingresso {}
