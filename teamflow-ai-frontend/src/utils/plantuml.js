import plantumlEncoder from 'plantuml-encoder'

export const plantUmlServerBase = import.meta.env?.VITE_PLANTUML_SERVER || 'https://www.plantuml.com/plantuml'

export const canRenderPlantUml = () => Boolean(plantUmlServerBase)

export const getPlantUmlImageUrl = (source) => {
  if (!canRenderPlantUml() || !source) return ''
  try {
    const encoded = plantumlEncoder.encode(source)
    return `${plantUmlServerBase}/svg/${encoded}`
  } catch (error) {
    console.error(error)
    return ''
  }
}

export const plantUmlPreviewState = (source) => {
  if (!canRenderPlantUml()) {
    return {
      available: false,
      source,
      message: '未配置 PlantUML 渲染服务，已保留源码。'
    }
  }
  return {
    available: true,
    source,
    imageUrl: getPlantUmlImageUrl(source),
    message: 'PlantUML 渲染服务已配置。'
  }
}
