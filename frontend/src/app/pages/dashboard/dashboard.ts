import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../../core/api.service';
import { Dashboard, Insight } from '../../core/models';
import { LineChart } from '../../shared/line-chart';
import { BarList } from '../../shared/bar-list';
import { ORDER_STATUS } from '../../shared/badges';

@Component({
  selector: 'app-dashboard',
  imports: [LineChart, BarList, RouterLink, CurrencyPipe, DecimalPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Tableau de bord</h1><p class="muted">Vue d'ensemble de la chaîne logistique</p></div>
        <button (click)="load()">Actualiser</button>
      </div>

      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      @if (data(); as d) {
        <div class="grid kpis">
          <div class="card kpi"><div class="label">Chiffre d'affaires 30 j</div>
            <div class="value">{{ d.kpis.revenue30d | currency: 'MAD' : 'symbol-narrow' : '1.0-0' }}</div>
            <div class="sub" [style.color]="d.kpis.revenueGrowthPct >= 0 ? 'var(--ok)' : 'var(--bad)'">
              {{ d.kpis.revenueGrowthPct >= 0 ? '▲' : '▼' }} {{ d.kpis.revenueGrowthPct | number: '1.0-1' }} % vs 30 j précédents</div></div>
          <div class="card kpi"><div class="label">Livraisons à l'heure</div>
            <div class="value">{{ d.kpis.onTimeRate | number: '1.0-1' }} %</div><div class="sub">60 derniers jours</div></div>
          <div class="card kpi"><div class="label">Expéditions actives</div>
            <div class="value">{{ d.kpis.activeShipments }}</div>
            <div class="sub">{{ d.kpis.atRiskShipments }} à risque · {{ d.kpis.overdueShipments }} en retard</div></div>
          <div class="card kpi"><div class="label">Commandes en attente</div>
            <div class="value">{{ d.kpis.pendingOrders }}</div><div class="sub"><a routerLink="/orders">Voir les commandes</a></div></div>
          <div class="card kpi"><div class="label">Stock bas</div>
            <div class="value">{{ d.kpis.lowStockCount }} <span class="small muted">/ {{ d.kpis.totalProducts }}</span></div>
            <div class="sub">Valeur : {{ d.kpis.stockValue | currency: 'MAD' : 'symbol-narrow' : '1.0-0' }}</div></div>
        </div>

        <div class="grid cols-2">
          <section class="card"><h2>Chiffre d'affaires par jour (30 j)</h2>
            <app-line-chart [points]="revenuePoints()" /></section>
          <section class="card"><h2>Alertes de l'assistant IA</h2>
            @for (i of insights(); track i.title) {
              <div class="insight">
                <span class="badge" [class]="sev(i)">{{ sevLabel(i) }}</span>
                <div><strong>{{ i.title }}</strong><div class="small muted">{{ i.detail }}</div></div>
              </div>
            } @empty { <p class="muted">Chargement…</p> }
            <a routerLink="/ai" class="small">Ouvrir l'assistant IA →</a>
          </section>
        </div>

        <div class="grid cols-2">
          <section class="card"><h2>Top produits (30 j, quantités)</h2><app-bar-list [items]="topItems()" /></section>
          <section class="card"><h2>Fiabilité des transporteurs (90 j)</h2>
            <app-bar-list [items]="carrierItems()" [max]="100" />
            <h3 style="margin-top:1rem">Commandes par statut</h3>
            <div class="actions">
              @for (s of statuses(); track s.key) {
                <span class="badge" [class]="s.cls">{{ s.label }} : {{ s.count }}</span>
              }
            </div>
          </section>
        </div>
      } @else if (!error()) { <div class="spinner">Chargement…</div> }
    </div>
  `,
  styles: `
    .insight { display: flex; gap: .6rem; align-items: start; padding: .5rem 0; border-bottom: 1px solid var(--line); }
    .insight:last-of-type { border-bottom: 0; } .insight .badge { margin-top: .15rem; }
    a { color: var(--brand); }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly insights = signal<Insight[]>([]);
  protected readonly error = signal('');

  protected readonly revenuePoints = computed(() =>
    (this.data()?.revenueByDay ?? []).map((r) => ({ label: r.date.slice(5), value: r.amount })));
  protected readonly topItems = computed(() =>
    (this.data()?.topProducts ?? []).map((p) => ({ label: p.name, value: p.quantity })));
  protected readonly carrierItems = computed(() =>
    (this.data()?.carrierPerformance ?? []).map((c) => ({
      label: `${c.carrier} (${c.shipments} liv.)`, value: c.onTimeRate, display: `${c.onTimeRate} %`,
      tone: c.onTimeRate >= 80 ? ('ok' as const) : c.onTimeRate >= 65 ? ('warn' as const) : ('bad' as const),
    })));
  protected readonly statuses = computed(() =>
    Object.entries(this.data()?.ordersByStatus ?? {}).map(([key, count]) => ({
      key, count, label: ORDER_STATUS[key]?.label ?? key, cls: ORDER_STATUS[key]?.cls ?? 'b-mute',
    })));

  constructor() { this.load(); }

  protected load() {
    this.error.set('');
    this.api.dashboard().subscribe({ next: (d) => this.data.set(d), error: (e) => this.error.set(errorMessage(e)) });
    this.api.insights().subscribe({ next: (i) => this.insights.set(i), error: () => this.insights.set([]) });
  }
  protected sev(i: Insight) { return i.severity === 'HIGH' ? 'b-bad' : i.severity === 'MEDIUM' ? 'b-warn' : 'b-info'; }
  protected sevLabel(i: Insight) { return i.severity === 'HIGH' ? 'Urgent' : i.severity === 'MEDIUM' ? 'Attention' : 'Info'; }
}
