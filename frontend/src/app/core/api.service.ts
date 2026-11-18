import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import {
  Customer, Dashboard, DelayPrediction, Forecast, Insight, ModelReport, Movement, Order, OrderStatus,
  Product, ProductRequest, Shipment,
} from './models';

const API = environment.apiUrl;

/** Extrait un message lisible d'une erreur HTTP renvoyée par l'API. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) { return "Impossible de joindre le serveur. Le backend est-il démarré ?"; }
    const body = err.error;
    if (body?.fieldErrors) {
      return Object.entries(body.fieldErrors).map(([k, v]) => `${k} : ${v}`).join(' · ');
    }
    return body?.message ?? `Erreur ${err.status}`;
  }
  return 'Une erreur inattendue est survenue.';
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Référentiels
  meta() { return this.http.get<{ cities: string[]; carriers: string[] }>(`${API}/meta`); }
  customers() { return this.http.get<Customer[]>(`${API}/customers`); }
  createCustomer(c: Pick<Customer, 'name' | 'city' | 'email' | 'phone'>) { return this.http.post<Customer>(`${API}/customers`, c); }

  // Produits & stock
  products(search = '') {
    let params = new HttpParams();
    if (search.trim()) { params = params.set('search', search.trim()); }
    return this.http.get<Product[]>(`${API}/products`, { params });
  }
  createProduct(p: ProductRequest) { return this.http.post<Product>(`${API}/products`, p); }
  updateProduct(id: number, p: ProductRequest) { return this.http.put<Product>(`${API}/products/${id}`, p); }
  deleteProduct(id: number) { return this.http.delete<void>(`${API}/products/${id}`); }
  receive(id: number, quantity: number, reference: string) {
    return this.http.post<Product>(`${API}/products/${id}/receive`, { quantity, reference });
  }
  movements(id: number) { return this.http.get<Movement[]>(`${API}/products/${id}/movements`); }

  // Commandes
  orders(status?: OrderStatus) {
    const params = status ? new HttpParams().set('status', status) : new HttpParams();
    return this.http.get<Order[]>(`${API}/orders`, { params });
  }
  createOrder(customerId: number, lines: { productId: number; quantity: number }[]) {
    return this.http.post<Order>(`${API}/orders`, { customerId, lines });
  }
  confirmOrder(id: number, carrier?: string) {
    const params = carrier ? new HttpParams().set('carrier', carrier) : new HttpParams();
    return this.http.post<Order>(`${API}/orders/${id}/confirm`, null, { params });
  }
  cancelOrder(id: number) { return this.http.post<Order>(`${API}/orders/${id}/cancel`, null); }

  // Expéditions
  shipments(status?: string) {
    const params = status ? new HttpParams().set('status', status) : new HttpParams();
    return this.http.get<Shipment[]>(`${API}/shipments`, { params });
  }
  dispatch(id: number) { return this.http.post<Shipment>(`${API}/shipments/${id}/dispatch`, null); }
  deliver(id: number, delayDays?: number) {
    const params = delayDays === undefined ? new HttpParams() : new HttpParams().set('delayDays', delayDays);
    return this.http.post<Shipment>(`${API}/shipments/${id}/deliver`, null, { params });
  }

  // Tableau de bord & IA
  dashboard() { return this.http.get<Dashboard>(`${API}/dashboard`); }
  forecast() { return this.http.get<Forecast[]>(`${API}/ai/forecast`); }
  delayRisk() { return this.http.get<Shipment[]>(`${API}/ai/delay-risk`); }
  insights() { return this.http.get<Insight[]>(`${API}/ai/insights`); }
  model() { return this.http.get<ModelReport>(`${API}/ai/model`); }
  retrain() { return this.http.post<ModelReport>(`${API}/ai/retrain`, null); }
  predictDelay(destinationCity: string, weightKg: number, shipDate?: string) {
    return this.http.post<DelayPrediction>(`${API}/ai/predict-delay`, { destinationCity, weightKg, shipDate: shipDate || null });
  }
}
