/**
 * Formato COP (Pesos Colombianos)
 * Ej: 1234.5 -> "$1.234,50"
 */
export function formatCOP(amount) {
  if (amount == null) return '$0,00';
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  if (isNaN(num)) return '$0,00';
  // Redondear a 2 decimales
  const rounded = Math.round(num * 100) / 100;
  const parts = rounded.toFixed(2).split('.');
  const intPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `$${intPart},${parts[1]}`;
}
