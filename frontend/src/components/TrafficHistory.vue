<template>
  <section class="history-panel">
    <h2>Histórico de congestión</h2>

    <div class="range-selector">
      <button :class="{ active: range === 'day' }" @click="setRange('day')">Día</button>
      <button :class="{ active: range === 'week' }" @click="setRange('week')">Semana</button>
    </div>

    <div class="chart-wrapper">
      <canvas ref="chartCanvas" />
    </div>

    <p v-if="error" class="error-msg">{{ error }}</p>
  </section>
</template>

<script setup>
/**
 * TrafficHistory.vue — gráfico de barras histórico con Chart.js
 * Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import {
  Chart,
  BarController,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
  Title
} from 'chart.js'
import { useTrafficStore } from '../stores/trafficStore'
import { authFetch } from '../services/api'

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend, Title)

const store = useTrafficStore()
const chartCanvas = ref(null)
const range = ref('day')
const error = ref('')

let chartInstance = null

/** Devuelve el color de barra según speedRatio. Requisito 7.4 */
function barColor(speedRatio) {
  if (speedRatio >= 0.70) return '#22c55e'
  if (speedRatio >= 0.30) return '#f59e0b'
  return '#ef4444'
}

/** Consulta el histórico y actualiza el store. */
async function fetchHistory() {
  error.value = ''
  try {
    const res = await authFetch(`/api/history?range=${range.value}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    store.setHistory(data)
  } catch (e) {
    error.value = 'Error cargando el histórico. Intenta de nuevo.'
    console.error('[TrafficHistory]', e)
  }
}

/** Inicializa o actualiza el gráfico Chart.js. Requisitos 7.1, 7.2, 7.3, 7.5 */
function renderChart(data) {
  if (!chartCanvas.value) return

  const labels = data.map((d) => d.label)
  const values = data.map((d) => d.avgSpeedRatio ?? 0)
  const colors = values.map(barColor)

  if (chartInstance) {
    chartInstance.data.labels = labels
    chartInstance.data.datasets[0].data = values
    chartInstance.data.datasets[0].backgroundColor = colors
    chartInstance.update()
    return
  }

  chartInstance = new Chart(chartCanvas.value, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Velocidad relativa (speedRatio)',
          data: values,
          backgroundColor: colors,
          borderRadius: 4,
          borderSkipped: false
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        title: {
          display: true,
          text: 'Congestión vial en Medellín',
          color: '#f1f5f9',
          font: { size: 14, weight: 'bold' }
        },
        legend: {
          labels: { color: '#94a3b8' }
        },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const v = ctx.raw
              let nivel = v >= 0.70 ? 'Fluido' : v >= 0.30 ? 'Moderado' : 'Severo'
              return ` speedRatio: ${v.toFixed(2)} — ${nivel}`
            }
          }
        }
      },
      scales: {
        x: {
          ticks: { color: '#94a3b8' },
          grid: { color: 'rgba(148,163,184,0.1)' },
          title: {
            display: true,
            text: range.value === 'day' ? 'Hora del día' : 'Día de la semana',
            color: '#94a3b8'
          }
        },
        y: {
          min: 0,
          max: 1,
          ticks: { color: '#94a3b8' },
          grid: { color: 'rgba(148,163,184,0.1)' },
          title: {
            display: true,
            text: 'Velocidad relativa (0 = trancón, 1 = libre)',
            color: '#94a3b8'
          }
        }
      }
    }
  })
}

function setRange(newRange) {
  range.value = newRange
}

onMounted(async () => {
  await fetchHistory()
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.destroy()
    chartInstance = null
  }
})

watch(range, async () => {
  await fetchHistory()
  // Actualizar título del eje X
  if (chartInstance) {
    chartInstance.options.scales.x.title.text =
      range.value === 'day' ? 'Hora del día' : 'Día de la semana'
  }
})

watch(() => store.history, (data) => {
  if (data && data.length > 0) renderChart(data)
})
</script>

<style scoped>
.history-panel {
  padding: 1rem;
  background: #1e293b;
  border-top: 1px solid #334155;
}

h2 {
  font-size: 1rem;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 0.75rem;
}

.range-selector {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.range-selector button {
  padding: 0.35rem 1rem;
  border: 1px solid #475569;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.15s;
}

.range-selector button.active,
.range-selector button:hover {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}

.chart-wrapper {
  height: 220px;
  position: relative;
}

.error-msg {
  color: #f87171;
  font-size: 0.8rem;
  margin-top: 0.5rem;
}
</style>
