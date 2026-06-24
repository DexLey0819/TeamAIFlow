<template>
  <div class="section-chart-block">
    <div ref="chartRef" class="section-chart-block__canvas" role="img" :aria-label="chartTitle"></div>
    <p v-if="!hasChartData" class="section-chart-block__empty">暂无图表数据</p>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  chart: {
    type: Object,
    default: () => ({})
  }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

const supportedTypes = ['bar', 'line', 'pie']

const toNumber = (value) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

const valueOfDatum = (datum) => {
  if (datum && typeof datum === 'object' && 'value' in datum) {
    return datum.value
  }
  return datum
}

const normalizeSeries = (series) => {
  if (!Array.isArray(series)) return []

  return series.map((item, index) => {
    const record = item && typeof item === 'object' ? item : {}
    const data = Array.isArray(record.data) ? record.data : []

    return {
      name: record.name ? String(record.name) : `系列 ${index + 1}`,
      data: data.map((datum) => toNumber(valueOfDatum(datum)))
    }
  })
}

const fallbackCategories = (series) => {
  const length = series.reduce((max, item) => Math.max(max, item.data.length), 0)
  return Array.from({ length }, (_, index) => `项目 ${index + 1}`)
}

const normalizeChart = (chart) => {
  const record = chart && typeof chart === 'object' && !Array.isArray(chart) ? chart : {}
  const type = supportedTypes.includes(record.type) ? record.type : 'bar'
  const series = normalizeSeries(record.series)
  const categories = Array.isArray(record.categories)
    ? record.categories.map((item) => String(item))
    : fallbackCategories(series)

  return {
    type,
    title: record.title ? String(record.title) : '',
    categories: categories.length ? categories : fallbackCategories(series),
    series
  }
}

const normalizedChart = computed(() => normalizeChart(props.chart))
const chartTitle = computed(() => normalizedChart.value.title || '图表预览')
const hasChartData = computed(() => normalizedChart.value.series.some((item) => item.data.length > 0))

const buildAxisOption = (chart) => ({
  animationDuration: 240,
  title: chart.title
    ? {
        text: chart.title,
        left: 'center',
        top: 0,
        textStyle: {
          color: '#162033',
          fontSize: 14,
          fontWeight: 600
        }
      }
    : undefined,
  tooltip: { trigger: 'axis' },
  legend: {
    top: chart.title ? 28 : 0,
    type: 'scroll'
  },
  grid: {
    top: chart.title ? 64 : 36,
    right: 24,
    bottom: 32,
    left: 32,
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: chart.categories,
    axisLabel: { color: '#667085' }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#667085' },
    splitLine: { lineStyle: { color: '#edf2f7' } }
  },
  series: chart.series.map((item) => ({
    type: chart.type,
    name: item.name,
    data: item.data,
    smooth: chart.type === 'line',
    emphasis: { focus: 'series' }
  }))
})

const buildPieOption = (chart) => {
  const firstSeries = chart.series[0] || { name: '数值', data: [] }
  const data = firstSeries.data.map((value, index) => ({
    name: chart.categories[index] || `项目 ${index + 1}`,
    value
  }))

  return {
    animationDuration: 240,
    title: chart.title
      ? {
          text: chart.title,
          left: 'center',
          top: 0,
          textStyle: {
            color: '#162033',
            fontSize: 14,
            fontWeight: 600
          }
        }
      : undefined,
    tooltip: { trigger: 'item' },
    legend: {
      type: 'scroll',
      bottom: 0
    },
    series: [
      {
        type: 'pie',
        name: firstSeries.name,
        radius: ['35%', '66%'],
        center: ['50%', chart.title ? '53%' : '48%'],
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(15, 23, 42, 0.18)'
          }
        }
      }
    ]
  }
}

const buildChartOption = (chart) => (
  chart.type === 'pie' ? buildPieOption(chart) : buildAxisOption(chart)
)

const resizeChart = () => {
  chartInstance?.resize()
}

const renderChart = async () => {
  await nextTick()
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  chartInstance.setOption(buildChartOption(normalizedChart.value), true)
}

onMounted(() => {
  renderChart()

  if (typeof ResizeObserver !== 'undefined' && chartRef.value) {
    resizeObserver = new ResizeObserver(resizeChart)
    resizeObserver.observe(chartRef.value)
  } else {
    window.addEventListener('resize', resizeChart)
  }
})

watch(
  () => props.chart,
  () => {
    renderChart()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resizeChart)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.section-chart-block {
  position: relative;
  min-height: 320px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.section-chart-block__canvas {
  width: 100%;
  height: 320px;
}

.section-chart-block__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  color: #98a2b3;
  font-size: 13px;
  pointer-events: none;
}
</style>
