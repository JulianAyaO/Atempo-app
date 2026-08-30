export const STATUS_COLORS = {
  FREE: '#4ade80',
  OCCUPIED_EMPTY: '#22d3ee',
  DRAFT: '#fb923c',
  PENDING: '#f59e0b',
  IN_PREPARATION: '#3b82f6',
  READY: '#22c55e',
  DELIVERED: '#8b5cf6',
  PAYMENT_REQUESTED: '#a855f7',
  PAID: '#10b981',
  CLOSED: '#64748b',
  CANCELLED: '#ef4444',
};

export const STATUS_LABELS = {
  FREE: 'Libre',
  OCCUPIED_EMPTY: 'Ocupada · sin pedido',
  DRAFT: 'Borrador',
  PENDING: 'Pendiente',
  IN_PREPARATION: 'En preparación',
  READY: 'Listo',
  DELIVERED: 'Entregado',
  PAYMENT_REQUESTED: 'Cuenta solicitada',
  PAID: 'Pagado',
  CLOSED: 'Cerrado',
  CANCELLED: 'Cancelado',
};

export const STATUS_LABELS_WITH_ICON = {
  FREE: 'Libre',
  OCCUPIED_EMPTY: 'Ocupada · sin pedido',
  DRAFT: 'Borrador',
  PENDING: 'Pendiente',
  IN_PREPARATION: 'En preparación',
  READY: 'Listo',
  DELIVERED: 'Entregado',
  PAYMENT_REQUESTED: 'Cuenta solicitada',
  PAID: 'Pagado',
  CLOSED: 'Cerrado',
  CANCELLED: 'Cancelado',
};

export const KITCHEN_NEXT_STATUS = {
  PENDING: 'IN_PREPARATION',
  IN_PREPARATION: 'READY',
};
