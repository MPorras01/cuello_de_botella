/**
 * Modo demo: genera segmentos de tráfico simulados sobre las vías principales
 * de Medellín y su área metropolitana. Se usa cuando las fuentes externas
 * (Google/Waze/SIMM) no están configuradas, para que la interfaz muestre
 * los trancones en el mapa.
 */

const FREE_FLOW = 50.0

/** Corredores viales reales del Valle de Aburrá con sus coordenadas aproximadas. */
const DEMO_ROUTES = [
  {
    name: 'Autopista Norte · Bello',
    ratio: 0.14,
    coords: [
      [-75.5755, 6.2550],
      [-75.5732, 6.2700],
      [-75.5705, 6.2850],
      [-75.5685, 6.3000]
    ]
  },
  {
    name: 'Av. El Poblado · Obelisco',
    ratio: 0.24,
    coords: [
      [-75.5900, 6.2040],
      [-75.5855, 6.2110],
      [-75.5812, 6.2210]
    ]
  },
  {
    name: 'Avenida 80 · Estadio',
    ratio: 0.17,
    coords: [
      [-75.6100, 6.2500],
      [-75.6070, 6.2420],
      [-75.6035, 6.2330]
    ]
  },
  {
    name: 'Av. Las Vegas · Envigado',
    ratio: 0.44,
    coords: [
      [-75.5860, 6.1710],
      [-75.5810, 6.1630],
      [-75.5768, 6.1560]
    ]
  },
  {
    name: 'Autopista Sur · Itagüí',
    ratio: 0.33,
    coords: [
      [-75.6020, 6.1400],
      [-75.5975, 6.1300],
      [-75.5930, 6.1210]
    ]
  },
  {
    name: 'Av. Oriental · Guayabal',
    ratio: 0.56,
    coords: [
      [-75.5730, 6.2150],
      [-75.5722, 6.2220],
      [-75.5710, 6.2310]
    ]
  },
  {
    name: 'Calle San Juan · Centro',
    ratio: 0.47,
    coords: [
      [-75.5810, 6.2510],
      [-75.5750, 6.2510],
      [-75.5665, 6.2510]
    ]
  },
  {
    name: 'Av. La Playa · Centro',
    ratio: 0.79,
    coords: [
      [-75.5680, 6.2480],
      [-75.5725, 6.2490],
      [-75.5785, 6.2490]
    ]
  },
  {
    name: 'Av. Regional · Belén',
    ratio: 0.31,
    coords: [
      [-75.5900, 6.2310],
      [-75.5860, 6.2200],
      [-75.5820, 6.2090]
    ]
  },
  {
    name: 'Av. Bolívar · Niquía',
    ratio: 0.66,
    coords: [
      [-75.5660, 6.2560],
      [-75.5620, 6.2680],
      [-75.5580, 6.2790]
    ]
  }
]

/** Derivar nivel de congestión desde speedRatio (escala estilo Waze). */
function levelFromRatio(ratio) {
  if (ratio >= 0.75) return 'fluido'
  if (ratio >= 0.50) return 'lento'
  if (ratio >= 0.30) return 'congestionado'
  return 'severo'
}

function clamp(v, min, max) {
  return Math.min(max, Math.max(min, v))
}

/** Punto medio de una polilínea (para el marcador de cuello de botella). */
function midpoint(coords) {
  const mid = coords[Math.floor(coords.length / 2)]
  return { lat: mid[1], lng: mid[0] }
}

/**
 * Genera los segmentos demo del ciclo actual. El ratio varía levemente en
 * cada llamada para que el mapa se sienta "en vivo".
 */
export function buildDemoSegments() {
  return DEMO_ROUTES.map((route, idx) => {
    const jitter = (Math.random() - 0.5) * 0.12
    const ratio = clamp(route.ratio + jitter, 0.06, 0.95)
    const currentSpeed = Math.round(ratio * FREE_FLOW * 10) / 10
    const { lat, lng } = midpoint(route.coords)
    return {
      segmentId: `demo-${idx}`,
      segmentName: route.name,
      currentSpeed,
      freeFlowSpeed: FREE_FLOW,
      speedRatio: Math.round(ratio * 1000) / 1000,
      congestionLevel: levelFromRatio(ratio),
      geometry: { type: 'LineString', coordinates: route.coords },
      lat,
      lng
    }
  })
}
