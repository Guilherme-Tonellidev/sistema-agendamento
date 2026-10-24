import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BarraCarregamento } from './compartilhado/barra-carregamento/barra-carregamento';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BarraCarregamento],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
