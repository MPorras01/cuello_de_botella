/**
 * Niveles de congestión estilo Waze (4 colores).
 * Se derivan del speedRatio (velocidad actual / velocidad libre).
 */

export const LEVELS = [
  { key: 'fluido', label: 'Fluido', color: '#22c55e' },
  { key: 'lento', label: 'Lento', color: '#eab308' },
  { key: 'congestionado', label: 'Congestión', color: '#f97316' },
  { key: 'severo', label: '¡Trancón!', color: '#ef4444' }
]

export function levelFromRatio(ratio) {
  if (ratio >= 0.75) return LEVELS[0]
  if (ratio >= 0.50) return LEVELS[1]
  if (ratio >= 0.30) return LEVELS[2]
  return LEVELS[3]
}

export function colorFromRatio(ratio) {
  return levelFromRatio(ratio).color
}

export function labelNivel(ratio) {
  return levelFromRatio(ratio).label
}
