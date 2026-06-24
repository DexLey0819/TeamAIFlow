import { computed } from 'vue'

export const toPositiveInteger = (raw) => {
  if (Array.isArray(raw) || raw == null) return null
  const text = String(raw)
  if (!/^[1-9]\d*$/.test(text)) return null
  const value = Number(text)
  return Number.isSafeInteger(value) ? value : null
}

export const usePositiveRouteParam = (route, name) => computed(() => toPositiveInteger(route.params[name]))

export const usePositiveProjectId = (route) => usePositiveRouteParam(route, 'id')

export const usePositiveSectionId = (route) => usePositiveRouteParam(route, 'sectionId')

export const isManagementDeliverySection = (raw) => !Array.isArray(raw) && raw === 'management-delivery'
