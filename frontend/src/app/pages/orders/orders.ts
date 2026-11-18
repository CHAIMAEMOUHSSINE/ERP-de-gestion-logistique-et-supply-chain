import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { Customer, Order, OrderStatus, Product } from '../../core/models';
import { ORDER_STATUS } from '../../shared/badges';

interface DraftLine { productId: number | null; quantity: number; }

@Component({
  selector: 'app-orders',
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Commandes</h1><p class="muted">Confirmer une commande réserve le stock et crée l'expédition (transporteur choisi par l'IA)</p></div>
        <div class="actions">
          <select [ngModel]="filter()" (ngModelChange)="filter.set($event); load()" aria-label="Filtrer par statut">
            <option value="">Tous les statuts</option>
            @for (s of statusKeys; track s) { <option [value]="s">{{ label(s) }}</option> }
          </select>
          <button class="primary" (click)="openForm()">+ Nouvelle commande</button>
        </div>
      </div>

      @if (message()) { <div class="alert success" role="status">{{ message() }}</div> }
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }

      @if (formOpen()) {
        <form class="card" (ngSubmit)="create()" #f="ngForm">
          <h2>Nouvelle commande</h2>
          <label style="max-width:360px">Client
            <select name="customer" [(ngModel)]="customerId" required>
              <option [ngValue]="null" disabled>Choisir un client…</option>
              @for (c of customers(); track c.id) { <option [ngValue]="c.id">{{ c.name }} — {{ c.city }}</option> }
            </select>
          </label>
          <h3 style="margin-top:1rem">Lignes</h3>
          @for (l of lines; track $index) {
            <div class="form-grid line">
              <label>Produit
                <select [name]="'p' + $index" [(ngModel)]="l.productId" required>
                  <option [ngValue]="null" disabled>Choisir un produit…</option>
                  @for (p of products(); track p.id) { <option [ngValue]="p.id">{{ p.name }} (stock {{ p.stockQty }})</option> }
                </select>
              </label>
              <label>Quantité<input [name]="'q' + $index" type="number" min="1" [(ngModel)]="l.quantity" required /></label>
              <button type="button" class="sm danger" (click)="removeLine($index)" [disabled]="lines.length === 1">Retirer</button>
            </div>
          }
          <div class="actions" style="margin-top:.75rem">
            <button type="button" (click)="addLine()">+ Ajouter une ligne</button>
            <span class="muted">Total estimé : <strong class="num">{{ draftTotal() | number: '1.2-2' }} MAD</strong></span>
          </div>
          <div class="actions" style="margin-top:.9rem">
            <button class="primary" [disabled]="f.invalid">Créer la commande</button>
            <button type="button" (click)="formOpen.set(false)">Annuler</button>
          </div>
        </form>
      }

      <div class="card table-wrap">
        <table>
          <thead><tr><th>Référence</th><th>Client</th><th>Date</th><th class="right">Total (MAD)</th><th>Statut</th><th>Expédition</th><th></th></tr></thead>
          <tbody>
            @for (o of orders(); track o.id) {
              <tr class="clickable" (click)="toggle(o.id)">
                <td class="num"><strong>{{ o.reference }}</strong></td>
                <td>{{ o.customerName }}<div class="small muted">{{ o.city }}</div></td>
                <td class="num">{{ o.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td class="right num">{{ o.totalAmount | number: '1.2-2' }}</td>
                <td><span class="badge" [class]="cls(o.status)">{{ label(o.status) }}</span></td>
                <td>@if (o.shipment) { {{ o.shipment.reference }}<div class="small muted">{{ o.shipment.carrier }}</div> } @else { — }</td>
                <td class="actions" (click)="$event.stopPropagation()">
                  @if (o.status === 'PENDING') {
                    <select class="sm" [ngModel]="carrierFor(o.id)" (ngModelChange)="setCarrier(o.id, $event)" [attr.aria-label]="'Transporteur ' + o.reference">
                      <option value="">Transporteur : choix IA</option>
                      @for (c of carriers(); track c) { <option [value]="c">{{ c }}</option> }
                    </select>
                    <button class="sm primary" (click)="confirm(o)">Confirmer</button>
                  }
                  @if (o.status === 'PENDING' || o.status === 'CONFIRMED') { <button class="sm danger" (click)="cancel(o)">Annuler</button> }
                </td>
              </tr>
              @if (openId() === o.id) {
                <tr><td colspan="7">
                  <table class="inner">
                    <thead><tr><th>SKU</th><th>Produit</th><th class="right">Qté</th><th class="right">Prix unit.</th><th class="right">Total</th></tr></thead>
                    <tbody>
                      @for (l of o.lines; track l.productId) {
                        <tr><td class="num">{{ l.sku }}</td><td>{{ l.name }}</td><td class="right num">{{ l.quantity }}</td>
                          <td class="right num">{{ l.unitPrice | number: '1.2-2' }}</td><td class="right num">{{ l.lineTotal | number: '1.2-2' }}</td></tr>
                      }
                    </tbody>
                  </table>
                </td></tr>
              }
            } @empty { <tr><td colspan="7" class="empty">Aucune commande.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .line { align-items: end; margin-bottom: .5rem; } .inner { background: var(--bg); border-radius: 8px; }
    select.sm { padding: .25rem .4rem; font-size: .85rem; max-width: 190px; }
  `,
})
export class OrdersPage {
  private readonly api = inject(ApiService);
  protected readonly statusKeys = Object.keys(ORDER_STATUS);
  protected readonly orders = signal<Order[]>([]);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly carriers = signal<string[]>([]);
  protected readonly filter = signal<'' | OrderStatus>('');
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly formOpen = signal(false);
  protected readonly openId = signal<number | null>(null);
  private readonly chosenCarrier = signal<Record<number, string>>({});
  protected customerId: number | null = null;
  protected lines: DraftLine[] = [{ productId: null, quantity: 1 }];

  protected readonly draftTotal = computed(() => {
    this.products(); // dépendance réactive
    return this.lines.reduce((sum, l) => sum + (this.products().find((p) => p.id === l.productId)?.unitPrice ?? 0) * (l.quantity || 0), 0);
  });

  constructor() {
    this.load();
    this.api.customers().subscribe((c) => this.customers.set(c));
    this.api.products().subscribe((p) => this.products.set(p));
    this.api.meta().subscribe((m) => this.carriers.set(m.carriers));
  }

  protected load() {
    const status = this.filter();
    this.api.orders(status || undefined).subscribe({
      next: (o) => this.orders.set(o), error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected label(s: string) { return ORDER_STATUS[s]?.label ?? s; }
  protected cls(s: string) { return ORDER_STATUS[s]?.cls ?? 'b-mute'; }
  protected toggle(id: number) { this.openId.set(this.openId() === id ? null : id); }
  protected carrierFor(id: number) { return this.chosenCarrier()[id] ?? ''; }
  protected setCarrier(id: number, c: string) { this.chosenCarrier.update((m) => ({ ...m, [id]: c })); }

  protected openForm() {
    this.customerId = null; this.lines = [{ productId: null, quantity: 1 }]; this.error.set('');
    this.api.products().subscribe((p) => this.products.set(p));
    this.formOpen.set(true);
  }
  protected addLine() { this.lines = [...this.lines, { productId: null, quantity: 1 }]; }
  protected removeLine(i: number) { this.lines = this.lines.filter((_, idx) => idx !== i); }

  protected create() {
    if (this.customerId === null) { return; }
    const lines = this.lines.filter((l) => l.productId !== null).map((l) => ({ productId: l.productId as number, quantity: l.quantity }));
    this.api.createOrder(this.customerId, lines).subscribe({
      next: (o) => { this.formOpen.set(false); this.flash(`Commande ${o.reference} créée.`); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected confirm(o: Order) {
    this.api.confirmOrder(o.id, this.carrierFor(o.id) || undefined).subscribe({
      next: (r) => { this.flash(`${r.reference} confirmée : expédition ${r.shipment?.reference} avec ${r.shipment?.carrier}.`); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected cancel(o: Order) {
    if (!confirm(`Annuler la commande ${o.reference} ?`)) { return; }
    this.api.cancelOrder(o.id).subscribe({
      next: () => { this.flash(`${o.reference} annulée.`); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  private flash(text: string) { this.error.set(''); this.message.set(text); setTimeout(() => this.message.set(''), 5000); }
}
