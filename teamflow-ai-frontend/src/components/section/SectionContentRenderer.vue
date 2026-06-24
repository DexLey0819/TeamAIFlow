<template>
  <div class="section-content-renderer">
    <template v-if="renderedBlocks.length">
      <section
        v-for="block in renderedBlocks"
        :key="block.key"
        class="section-content-renderer__block"
        :class="`section-content-renderer__block--${block.type}`"
      >
        <div v-if="block.type === 'text'" class="section-content-renderer__text">
          <p v-for="(paragraph, paragraphIndex) in block.paragraphs" :key="paragraphIndex" v-text="paragraph"></p>
        </div>

        <h2 v-else-if="block.type === 'heading' && block.level === 2" class="section-content-renderer__h2">{{ block.text }}</h2>
        <h3 v-else-if="block.type === 'heading' && block.level === 3" class="section-content-renderer__h3">{{ block.text }}</h3>
        <h4 v-else-if="block.type === 'heading' && block.level >= 4" class="section-content-renderer__h4">{{ block.text }}</h4>

        <div v-else-if="block.type === 'table'" class="section-content-renderer__table-wrap">
          <table>
            <thead>
              <tr>
                <th v-for="(header, headerIndex) in block.headers" :key="headerIndex" v-text="header"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                <td v-for="(header, cellIndex) in block.headers" :key="cellIndex" v-text="cellText(row[cellIndex])"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <SectionChartBlock v-else-if="block.type === 'chart'" :chart="block.chart" />

        <div v-else-if="block.type === 'plantuml'" class="section-content-renderer__plantuml" :class="{ 'is-rendered': block.preview.available }">
          <div
            v-if="!block.preview.available"
            class="section-content-renderer__plantuml-status"
          >
            {{ block.preview.message }}
          </div>
          <img
            v-if="block.preview.available && block.preview.imageUrl"
            :src="block.preview.imageUrl"
            alt="PlantUML Diagram"
            class="plantuml-image"
          />
          <pre v-else>{{ block.source }}</pre>
        </div>

        <figure v-else-if="block.type === 'image'" class="section-content-renderer__image">
          <SectionImage :src="block.url" :alt="block.alt || '图片'" />
        </figure>
      </section>
    </template>

    <p v-else class="section-content-renderer__empty">暂无正文内容</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import SectionChartBlock from './SectionChartBlock.vue'
import SectionImage from './SectionImage.vue'
import { parseSectionBlocks } from '../../utils/sectionContentBlocks'
import { plantUmlPreviewState } from '../../utils/plantuml'

const props = defineProps({
  body: {
    type: String,
    default: ''
  }
})

const toParagraphs = (text) => (
  String(text || '')
    .split(/\n{2,}/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
)

const renderedBlocks = computed(() => (
  parseSectionBlocks(props.body).map((block, index) => {
    if (block.type === 'text') {
      return {
        ...block,
        key: `${block.type}-${index}`,
        paragraphs: toParagraphs(block.text)
      }
    }

    if (block.type === 'plantuml') {
      return {
        ...block,
        key: `${block.type}-${index}`,
        preview: plantUmlPreviewState(block.source)
      }
    }

    return {
      ...block,
      key: `${block.type}-${index}`
    }
  })
))

const cellText = (value) => (value == null ? '' : String(value))
</script>

<style scoped>
.section-content-renderer {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.7;
}

.section-content-renderer__block + .section-content-renderer__block {
  margin-top: 16px;
}

.section-content-renderer__text p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.section-content-renderer__text p + p {
  margin-top: 10px;
}

.section-content-renderer__table-wrap {
  overflow-x: auto;
}

.section-content-renderer__table-wrap table {
  width: 100%;
  min-width: 420px;
  border-collapse: collapse;
  border: 1px solid #e5e7eb;
}

.section-content-renderer__table-wrap th,
.section-content-renderer__table-wrap td {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  text-align: left;
  vertical-align: top;
  word-break: break-word;
}

.section-content-renderer__table-wrap th {
  background: #f8fafc;
  color: #162033;
  font-weight: 600;
}

.section-content-renderer__plantuml {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.section-content-renderer__plantuml.is-rendered {
  border: none;
  background: transparent;
  padding: 0;
  overflow-x: auto;
  width: 100%;
}

.section-content-renderer__plantuml-status {
  color: #667085;
  font-size: 13px;
}

.section-content-renderer__plantuml-status.is-available {
  color: #047857;
}

.section-content-renderer__plantuml pre {
  margin: 10px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.section-content-renderer__image {
  margin: 0;
}

.section-content-renderer__image img {
  display: block;
  max-width: 100%;
  max-height: 520px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  object-fit: contain;
}

.section-content-renderer__empty {
  margin: 0;
  color: #98a2b3;
  font-size: 13px;
}

.plantuml-image {
  display: block;
  max-width: none;
  max-height: none;
  margin-top: 10px;
}

.section-content-renderer__h2 {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin-top: 24px;
  margin-bottom: 12px;
}

.section-content-renderer__h3 {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin-top: 20px;
  margin-bottom: 10px;
}

.section-content-renderer__h4 {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  margin-top: 16px;
  margin-bottom: 8px;
}
</style>
