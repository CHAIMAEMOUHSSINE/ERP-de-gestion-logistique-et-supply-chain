import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout">
      <aside>
        <div class="brand"><span class="logo">LS</span> LogiSmart</div>
        <nav aria-label="Navigation principale">
          @for (item of items; track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active">
              <span aria-hidden="true">{{ item.icon }}</span> {{ item.label }}
            </a>
          }
        </nav>
        <div class="user">
          <div class="who">
            <strong>{{ auth.user()?.fullName }}</strong>
            <span class="muted small">{{ auth.user()?.role }}</span>
          </div>
          <button class="sm" (click)="auth.logout()">Déconnexion</button>
        </div>
      </aside>
      <main><router-outlet /></main>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 230px 1fr; min-height: 100vh; }
    aside { background: var(--surface); border-right: 1px solid var(--line); padding: 1rem .75rem;
      display: flex; flex-direction: column; gap: 1rem; position: sticky; top: 0; height: 100vh; }
    .brand { font-weight: 700; font-size: 1.1rem; display: flex; align-items: center; gap: .6rem; padding: 0 .4rem; }
    .logo { background: var(--brand); color: var(--brand-ink); border-radius: 8px; padding: .15rem .45rem; font-size: .85rem; }
    nav { display: flex; flex-direction: column; gap: .15rem; flex: 1; }
    nav a { text-decoration: none; color: var(--ink); padding: .55rem .7rem; border-radius: 9px; display: flex; gap: .6rem; }
    nav a:hover { background: var(--bg); } nav a.active { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
    .user { border-top: 1px solid var(--line); padding-top: .75rem; display: flex; flex-direction: column; gap: .5rem; }
    .who { display: flex; flex-direction: column; }
    main { padding: 1.5rem; min-width: 0; max-width: 1280px; width: 100%; }
    @media (max-width: 820px) {
      .layout { grid-template-columns: 1fr; }
      aside { position: static; height: auto; flex-direction: row; flex-wrap: wrap; align-items: center; }
      nav { flex-direction: row; flex-wrap: wrap; order: 3; width: 100%; flex: none; }
      .user { border: 0; padding: 0; margin-left: auto; flex-direction: row; align-items: center; }
      main { padding: 1rem; }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly items = [
    { path: '/dashboard', label: 'Tableau de bord', icon: '▦' },
    { path: '/products', label: 'Stock', icon: '▣' },
    { path: '/orders', label: 'Commandes', icon: '☰' },
    { path: '/shipments', label: 'Expéditions', icon: '➜' },
    { path: '/ai', label: 'Assistant IA', icon: '✦' },
  ];
}
