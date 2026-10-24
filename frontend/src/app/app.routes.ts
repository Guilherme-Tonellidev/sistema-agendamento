import { Routes } from '@angular/router';

import { exigePapel, somenteVisitante } from './core/autenticacao/guardas';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'horarios' },
  {
    path: 'entrar',
    title: 'Entrar — Barbearia Tonelli',
    canActivate: [somenteVisitante],
    loadComponent: () => import('./paginas/entrar/entrar').then((m) => m.Entrar),
  },
  {
    path: 'cadastro',
    title: 'Criar conta — Barbearia Tonelli',
    canActivate: [somenteVisitante],
    loadComponent: () => import('./paginas/cadastro/cadastro').then((m) => m.Cadastro),
  },
  {
    path: 'horarios',
    title: 'Escolher horário — Barbearia Tonelli',
    loadComponent: () => import('./paginas/horarios/horarios').then((m) => m.Horarios),
  },
  {
    path: 'servicos',
    title: 'Serviços — Barbearia Tonelli',
    loadComponent: () => import('./paginas/servicos/servicos').then((m) => m.Servicos),
  },
  {
    path: 'minhas-reservas',
    title: 'Minhas reservas — Barbearia Tonelli',
    canActivate: [exigePapel('CLIENTE')],
    loadComponent: () => import('./paginas/reservas/reservas').then((m) => m.Reservas),
  },
  {
    path: 'agenda',
    title: 'Agenda — Barbearia Tonelli',
    canActivate: [exigePapel('PROFISSIONAL', 'ADMIN')],
    loadComponent: () => import('./paginas/agenda/agenda').then((m) => m.Agenda),
  },
  { path: '**', redirectTo: '' },
];
