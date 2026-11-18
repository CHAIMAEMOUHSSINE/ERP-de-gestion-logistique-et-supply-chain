/** Libellés et classes de badge partagés par les pages. */
export const ORDER_STATUS: Record<string, { label: string; cls: string }> = {
  PENDING: { label: 'En attente', cls: 'b-warn' },
  CONFIRMED: { label: 'Confirmée', cls: 'b-info' },
  SHIPPED: { label: 'Expédiée', cls: 'b-info' },
  DELIVERED: { label: 'Livrée', cls: 'b-ok' },
  CANCELLED: { label: 'Annulée', cls: 'b-mute' },
};
export const SHIPMENT_STATUS: Record<string, { label: string; cls: string }> = {
  PLANNED: { label: 'Planifiée', cls: 'b-warn' },
  IN_TRANSIT: { label: 'En transit', cls: 'b-info' },
  DELIVERED: { label: 'Livrée', cls: 'b-ok' },
};
export const RISK: Record<string, { label: string; cls: string }> = {
  LOW: { label: 'Faible', cls: 'b-ok' },
  MEDIUM: { label: 'Moyen', cls: 'b-warn' },
  HIGH: { label: 'Élevé', cls: 'b-bad' },
};
export const STOCK_STATUS: Record<string, { label: string; cls: string }> = {
  CRITICAL: { label: 'Rupture probable', cls: 'b-bad' },
  WARNING: { label: 'À surveiller', cls: 'b-warn' },
  OK: { label: 'OK', cls: 'b-ok' },
};
export const pct = (v: number) => `${Math.round(v * 100)} %`;
