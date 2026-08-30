export function formatModifiers(modifiers) {
  if (!Array.isArray(modifiers) || modifiers.length === 0) return '';
  return modifiers
    .map(mod => {
      if (typeof mod === 'string') return mod;
      const name = mod?.ingredientName || mod?.name;
      if (!name) return '';
      if (mod?.type === 'REMOVE') return `Sin ${name}`;
      if (mod?.type === 'ADD') return `Con ${name}`;
      if (mod?.type === 'SUBSTITUTE') return `Sustituir ${name}`;
      return name;
    })
    .filter(Boolean)
    .join(', ');
}

export function formatItemName(item) {
  const mods = formatModifiers(item?.modifiers);
  return mods ? `${item.productName} (${mods.toLowerCase()})` : item?.productName;
}

function normalizeName(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim();
}

export function findProductInMenu(menuData, productId) {
  for (const cat of menuData || []) {
    const product = (cat.products || []).find(p => p.id === productId);
    if (product) return product;
  }
  return null;
}

export function buildCartIngredientRows(product, modifiers) {
  const ingredients = product?.ingredients || [];
  const removed = new Set();
  const added = new Set();

  for (const mod of modifiers || []) {
    const text = typeof mod === 'string' ? mod : formatModifiers([mod]);
    const lower = normalizeName(text);
    if (lower.startsWith('sin ')) removed.add(lower.slice(4).trim());
    else if (lower.startsWith('con ')) added.add(lower.slice(4).trim());
  }

  return ingredients.map((ing) => {
    const nameKey = normalizeName(ing.name);
    const nameStem = nameKey.replace(/es$/, '').replace(/s$/, '');
    const type = ing.type || 'BASE';
    const isRemoved = [...removed].some(r => {
      const rs = r.replace(/es$/, '').replace(/s$/, '');
      return r === nameKey || rs === nameStem || nameKey.includes(r) || r.includes(nameStem);
    });
    const isAdded = [...added].some(a => {
      const as = a.replace(/es$/, '').replace(/s$/, '');
      return a === nameKey || as === nameStem || nameKey.includes(a) || a.includes(nameStem);
    });
    let status = 'included';
    if (isRemoved && type !== 'BASE') status = 'removed';
    else if (type === 'OPTIONAL') status = isAdded ? 'added' : 'available';
    return { id: ing.id, name: ing.name, type, status };
  });
}
