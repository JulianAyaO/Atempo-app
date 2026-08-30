export const TABLE_NUMBERS = Array.from({ length: 10 }, (_, index) => index + 1);

export const STAFF_ROLE_SCREEN = {
  KITCHEN: 'Kitchen',
  WAITER: 'Waiter',
  ADMIN: 'Admin',
};

export const DEMO_STAFF_ACCOUNTS = [
  { name: 'admin', pin: '1234', label: 'Admin', subtitle: 'Panel de administración', icon: 'shield-checkmark' },
  { name: 'cocina', pin: '5678', label: 'Cocina', subtitle: 'Pedidos en preparación', icon: 'restaurant' },
  { name: 'mesero', pin: '9012', label: 'Mesero', subtitle: 'Entrega y cobro', icon: 'walk' },
];

export const POLLING_INTERVALS = {
  WAITER_ALERTS_MS: 3000,
  WAITER_DATA_MS: 10000,
  KITCHEN_DATA_MS: 15000,
  RELATIVE_TIME_MS: 60000,
};
