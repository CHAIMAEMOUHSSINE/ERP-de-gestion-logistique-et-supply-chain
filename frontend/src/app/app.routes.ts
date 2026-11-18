import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Tableau de bord', loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.DashboardPage) },
      { path: 'products', title: 'Stock', loadComponent: () => import('./pages/products/products').then((m) => m.ProductsPage) },
      { path: 'orders', title: 'Commandes', loadComponent: () => import('./pages/orders/orders').then((m) => m.OrdersPage) },
      { path: 'shipments', title: 'Expéditions', loadComponent: () => import('./pages/shipments/shipments').then((m) => m.ShipmentsPage) },
      { path: 'ai', title: 'Assistant IA', loadComponent: () => import('./pages/ai/ai').then((m) => m.AiPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
