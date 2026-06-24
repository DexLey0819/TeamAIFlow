export const tableTemplate = `| 列名 A | 列名 B |
| --- | --- |
| 示例 A | 示例 B |`

export const chartTemplate = (type = 'bar') => `\`\`\`teamflow-chart
{
  "type": "${type}",
  "title": "图表标题",
  "categories": ["类别 A", "类别 B", "类别 C"],
  "series": [
    { "name": "数值", "data": [3, 5, 2] }
  ]
}
\`\`\``

export const plantUmlTemplate = `\`\`\`plantuml
@startuml
用户 -> 系统: 提交内容
系统 --> 用户: 返回结果
@enduml
\`\`\``

export const imageMarkdown = (alt, url) => `![${alt || '图片'}](${url})`

export const appendBlock = (body, block) => [body || '', block].filter(Boolean).join('\n\n')

const fencePattern = /^```([\w-]+)?\s*$/
const closingFencePattern = /^```\s*$/
const imagePattern = /^!\[([^\]]*)\]\(([^)]+)\)\s*$/

const isTableLine = (line) => {
  const trimmed = line.trim()
  return trimmed.startsWith('|') && trimmed.endsWith('|') && trimmed.length > 1
}

const parseTableCells = (line) => (
  line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim())
)

const isSeparatorCell = (cell) => /^:?-{3,}:?$/.test(cell.trim())

const findSeparatorIndex = (tableLines) => (
  tableLines.findIndex((line) => {
    const cells = parseTableCells(line)
    return cells.length > 0 && cells.every(isSeparatorCell)
  })
)

const parseTable = (tableLines) => {
  const separatorIndex = findSeparatorIndex(tableLines)
  if (separatorIndex <= 0) return null

  const headers = parseTableCells(tableLines[separatorIndex - 1])
  const rows = tableLines
    .slice(separatorIndex + 1)
    .map(parseTableCells)

  return { type: 'table', headers, rows }
}

const findClosingFenceIndex = (lines, startIndex) => {
  for (let index = startIndex + 1; index < lines.length; index += 1) {
    if (closingFencePattern.test(lines[index].trim())) {
      return index
    }
  }
  return -1
}

export const parseSectionBlocks = (body) => {
  if (!body || typeof body !== 'string') return []

  const lines = body.split(/\r?\n/)
  const blocks = []
  const textBuffer = []

  const flushText = () => {
    const text = textBuffer.join('\n').trim()
    if (text) {
      blocks.push({ type: 'text', text })
    }
    textBuffer.length = 0
  }

  const headingPattern = /^\s*(#{1,6})\s+(.+)$/

  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()
    
    const headingMatch = line.match(headingPattern)
    if (headingMatch) {
      flushText()
      blocks.push({
        type: 'heading',
        level: headingMatch[1].length,
        text: headingMatch[2].trim()
      })
      index += 1
      continue
    }

    const fenceMatch = trimmed.match(fencePattern)

    if (fenceMatch) {
      const language = fenceMatch[1] || ''
      const closingIndex = findClosingFenceIndex(lines, index)

      if (closingIndex === -1) {
        textBuffer.push(...lines.slice(index))
        break
      }

      const blockLines = lines.slice(index, closingIndex + 1)
      const source = lines.slice(index + 1, closingIndex).join('\n').trim()

      if (language === 'teamflow-chart') {
        flushText()
        try {
          blocks.push({ type: 'chart', chart: JSON.parse(source) })
        } catch {
          blocks.push({ type: 'text', text: blockLines.join('\n') })
        }
      } else if (language === 'plantuml') {
        flushText()
        blocks.push({ type: 'plantuml', source })
      } else {
        textBuffer.push(...blockLines)
      }

      index = closingIndex + 1
      continue
    }

    const imageMatch = trimmed.match(imagePattern)
    if (imageMatch) {
      flushText()
      blocks.push({ type: 'image', alt: imageMatch[1], url: imageMatch[2] })
      index += 1
      continue
    }

    if (isTableLine(line)) {
      const tableLines = []
      let tableIndex = index

      while (tableIndex < lines.length && isTableLine(lines[tableIndex])) {
        tableLines.push(lines[tableIndex])
        tableIndex += 1
      }

      const table = parseTable(tableLines)
      if (table) {
        flushText()
        blocks.push(table)
      } else {
        textBuffer.push(...tableLines)
      }

      index = tableIndex
      continue
    }

    textBuffer.push(line)
    index += 1
  }

  flushText()
  return blocks
}
