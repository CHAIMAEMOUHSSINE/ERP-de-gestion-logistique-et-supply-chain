import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Movement, Product, ProductRequest } from '../../core/models';

const EMPTY: ProductRequest = {
  sku: '', name: '', category: '', unitPrice: 0, weightKg: 0, stockQty: 0, reorderLevel: 0, leadTimeDays: 5,
};

@Component({
  selector: 'app-products',
  imports: [FormsModule, DecimalPipe, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Stock</h1><p class="muted">{{ products().length }} produit(s) · {{ lowCount() }} en stock bas</p></div>
        <div class="actions">
          <input type="search" placeholder="Rechercher (nom, SKU, catégorie)…" [ngModel]="search()" (ngModelChange)="onSearch($event)" aria-label="Rechercher" />
          <button class="primary" (click)="openForm()">+ Nouveau produit</button>
        </div>
      </div>

      @if (message()) { <div class="alert success" role="status">{{ message() }}</div> }
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }

      @if (formOpen()) {
        <form class="card" (ngSubmit)="save()" #f="ngForm">
          <h2>{{ editingId() ? 'Modifier le produit' : 'Nouveau produit' }}</h2>
          <div class="form-grid">
            <label>SKU<input name="sku" [(ngModel)]="form.sku" required /></label>
            <label>Nom<input name="name" [(ngModel)]="form.name" required /></label>
            <label>Catégorie<input name="category" [(ngModel)]="form.category" /></label>
            <label>Prix unitaire (MAD)<input name="price" type="number" min="0" step="0.01" [(ngModel)]="form.unitPrice" required /></label>
            <label>Poids unitaire (kg)<input name="weight" type="number" min="0" step="0.1" [(ngModel)]="form.weightKg" /></label>
            <label>Stock<input name="stock" type="number" min="0" [(ngModel)]="form.stockQty" required /></label>
            <label>Seuil de réappro.<input name="reorder" type="number" min="0" [(ngModel)]="form.reorderLevel" /></label>
            <label>Délai fournisseur (j)<input name="lead" type="number" min="1" [(ngModel)]="form.leadTimeDays" required /></label>
          </div>
          <div class="actions" style="margin-top:.9rem">
            <button class="primary" [disabled]="f.invalid">Enregistrer</button>
            <button type="button" (click)="formOpen.set(false)">Annuler</button>
          </div>
        </form>
      }

      <div class="card table-wrap">
        <table>
          <thead><tr><th>SKU</th><th>Produit</th><th>Catégorie</th><th class="right">Prix</th>
            <th class="right">Stock</th><th>État</th><th>Délai</th><th></th></tr></thead>
          <tbody>
            @for (p of products(); track p.id) {
              <tr>
                <td class="num">{{ p.sku }}</td><td>{{ p.name }}</td><td>{{ p.category }}</td>
                <td class="right num">{{ p.unitPrice | number: '1.2-2' }}</td>
                <td class="right num"><strong>{{ p.stockQty | number }}</strong></td>
                <td><span class="badge" [class]="p.lowStock ? 'b-bad' : 'b-ok'">{{ p.lowStock ? 'Stock bas' : 'OK' }}</span></td>
                <td class="num">{{ p.leadTimeDays }} j</td>
                <td class="actions">
                  <button class="sm" (click)="startReceive(p)">Réception</button>
                  <button class="sm" (click)="showMovements(p)">Historique</button>
                  <button class="sm" (click)="openForm(p)">Modifier</button>
                  @if (auth.isAdmin()) { <button class="sm danger" (click)="remove(p)">Supprimer</button> }
                </td>
              </tr>
            } @empty { <tr><td colspan="8" class="empty">Aucun produit.</td></tr> }
          </tbody>
        </table>
      </div>

      @if (receiving(); as r) {
        <form class="card" (ngSubmit)="receive()">
          <h2>Réception fournisseur : {{ r.name }}</h2>
          <div class="form-grid">
            <label>Quantité reçue<input name="qty" type="number" min="1" [(ngModel)]="receiveQty" required /></label>
            <label>Référence (bon de livraison)<input name="ref" [(ngModel)]="receiveRef" /></label>
          </div>
          <div class="actions" style="margin-top:.9rem">
            <button class="primary" [disabled]="receiveQty < 1">Valider la réception</button>
            <button type="button" (click)="receiving.set(null)">Annuler</button>
          </div>
        </form>
      }

      @if (history(); as h) {
        <div class="card">
          <div class="page-head"><h2>Mouvements : {{ h.product.name }}</h2><button class="sm" (click)="history.set(null)">Fermer</button></div>
          <div class="table-wrap"><table>
            <thead><tr><th>Date</th><th>Type</th><th class="right">Quantité</th><th>Motif</th><th>Référence</th></tr></thead>
            <tbody>
              @for (m of h.movements; track m.id) {
                <tr><td class="num">{{ m.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td><span class="badge" [class]="m.type === 'IN' ? 'b-ok' : 'b-warn'">{{ m.type === 'IN' ? 'Entrée' : 'Sortie' }}</span></td>
                  <td class="right num">{{ m.quantity | number }}</td><td>{{ reason(m.reason) }}</td><td>{{ m.reference }}</td></tr>
              } @empty { <tr><td colspan="5" class="empty">Aucun mouvement.</td></tr> }
            </tbody></table></div>
        </div>
      }
    </div>
  `,
})
export class ProductsPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly products = signal<Product[]>([]);
  protected readonly lowCount = computed(() => this.products().filter((p) => p.lowStock).length);
  protected readonly search = signal('');
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly receiving = signal<Product | null>(null);
  protected readonly history = signal<{ product: Product; movements: Movement[] } | null>(null);
  protected form: ProductRequest = { ...EMPTY };
  protected receiveQty = 1;
  protected receiveRef = '';
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() { this.load(); }

  protected load() {
    this.api.products(this.search()).subscribe({
      next: (p) => this.products.set(p), error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected onSearch(v: string) {
    this.search.set(v);
    if (this.timer) { clearTimeout(this.timer); }
    this.timer = setTimeout(() => this.load(), 250);
  }
  protected openForm(p?: Product) {
    this.error.set('');
    this.editingId.set(p?.id ?? null);
    this.form = p
      ? { sku: p.sku, name: p.name, category: p.category, unitPrice: p.unitPrice, weightKg: p.weightKg,
          stockQty: p.stockQty, reorderLevel: p.reorderLevel, leadTimeDays: p.leadTimeDays }
      : { ...EMPTY };
    this.formOpen.set(true);
  }
  protected save() {
    const id = this.editingId();
    const call = id ? this.api.updateProduct(id, this.form) : this.api.createProduct(this.form);
    call.subscribe({
      next: () => { this.formOpen.set(false); this.flash(id ? 'Produit modifié.' : 'Produit créé.'); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected remove(p: Product) {
    if (!confirm(`Supprimer « ${p.name} » ?`)) { return; }
    this.api.deleteProduct(p.id).subscribe({
      next: () => { this.flash('Produit supprimé.'); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected startReceive(p: Product) { this.receiveQty = 1; this.receiveRef = ''; this.receiving.set(p); }
  protected receive() {
    const p = this.receiving();
    if (!p) { return; }
    this.api.receive(p.id, this.receiveQty, this.receiveRef).subscribe({
      next: () => { this.receiving.set(null); this.flash(`Réception enregistrée pour ${p.name}.`); this.load(); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected showMovements(p: Product) {
    this.api.movements(p.id).subscribe({
      next: (movements) => this.history.set({ product: p, movements }), error: (e) => this.error.set(errorMessage(e)),
    });
  }
  protected reason(r: string) {
    return ({ ORDER: 'Commande', RECEPTION: 'Réception', CANCEL: 'Annulation', ADJUST: 'Ajustement' } as Record<string, string>)[r] ?? r;
  }
  private flash(text: string) {
    this.error.set(''); this.message.set(text);
    setTimeout(() => this.message.set(''), 4000);
  }
}
