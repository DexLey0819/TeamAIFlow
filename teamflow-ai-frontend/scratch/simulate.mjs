import { format } from 'node:util'

const calendar = {
  workDays: [1, 2, 3, 4, 5],
  holidays: [],
  specialWorkDays: [],
}

const isWorkingDay = (dateStr) => {
  if (!dateStr) return false
  const date = new Date(dateStr)
  const dayOfWeek = date.getUTCDay()
  if (calendar.specialWorkDays && calendar.specialWorkDays.includes(dateStr)) {
    return true
  }
  if (calendar.holidays && calendar.holidays.includes(dateStr)) {
    return false
  }
  return calendar.workDays.includes(dayOfWeek)
}

const getWorkingDaysCount = (startDateStr, endDateStr) => {
  if (!startDateStr || !endDateStr) return 0
  let start = new Date(startDateStr)
  let end = new Date(endDateStr)
  if (end < start) return 0
  let workDaysCount = 0
  let temp = new Date(start)
  while (temp <= end) {
    const tempStr = temp.toISOString().slice(0, 10)
    if (isWorkingDay(tempStr)) {
      workDaysCount++
    }
    temp.setUTCDate(temp.getUTCDate() + 1)
  }
  return workDaysCount
}

const getFirstWorkingDay = (dateStr) => {
  if (!dateStr) return ''
  let date = new Date(dateStr)
  let limit = 366
  while (limit > 0) {
    const s = date.toISOString().slice(0, 10)
    if (isWorkingDay(s)) {
      return s
    }
    date.setUTCDate(date.getUTCDate() + 1)
    limit--
  }
  return dateStr
}

const addWorkingDays = (startDateStr, duration) => {
  if (!startDateStr) return ''
  if (duration <= 0) return startDateStr
  
  let currentStart = getFirstWorkingDay(startDateStr)
  let date = new Date(currentStart)
  let remaining = duration
  let daysCounted = 1
  let limit = 366 * 5
  while (daysCounted < remaining && limit > 0) {
    date.setUTCDate(date.getUTCDate() + 1)
    const dateStr = date.toISOString().slice(0, 10)
    if (isWorkingDay(dateStr)) {
      daysCounted++
    }
    limit--
  }
  return date.toISOString().slice(0, 10)
}

const getAncestors = (wbs, task) => {
  const ancestors = []
  let currentLevel = task.level
  const idx = wbs.findIndex(t => t.id === task.id)
  if (idx === -1) return ancestors
  for (let i = idx - 1; i >= 0; i--) {
    const t = wbs[i]
    if (t.level < currentLevel) {
      ancestors.push(t)
      currentLevel = t.level
    }
  }
  return ancestors
}

const getTaskEffectiveEndDate = (wbs, task) => {
  if (!task) return ''
  if (!task.isParent) return task.endDate || ''
  const descendants = wbs.filter(c => !c.isParent && c.outlineCode && task.outlineCode && c.outlineCode.startsWith(task.outlineCode + '.'))
  if (!descendants.length) return task.endDate || ''
  const endDates = descendants.map(c => c.endDate).filter(Boolean)
  if (!endDates.length) return task.endDate || ''
  return endDates.reduce((max, d) => d > max ? d : max, endDates[0])
}

const runSchedule = (wbsData) => {
  const wbs = JSON.parse(JSON.stringify(wbsData))
  const taskMap = new Map(wbs.map(t => [t.wbsCode, t]))
  
  let changed = true
  let iterations = 0
  const maxIterations = 100
  
  while (changed && iterations < maxIterations) {
    changed = false
    iterations++
    
    // Pass 1
    for (let i = 0; i < wbs.length; i++) {
      const task = wbs[i]
      if (!task.isParent) {
        let expectedStart = ''
        
        let maxDirectPredecessorNextWorkingDay = null
        if (task.predecessors) {
          const predCodes = task.predecessors.split(',').map(s => s.trim()).filter(Boolean)
          predCodes.forEach(code => {
            const predTask = taskMap.get(code) || wbs.find(t => t.id === code)
            const predEndDate = getTaskEffectiveEndDate(wbs, predTask)
            if (predEndDate) {
              let nextDay = new Date(predEndDate)
              let found = false
              let limit = 366
              while (!found && limit > 0) {
                nextDay.setUTCDate(nextDay.getUTCDate() + 1)
                const nextDayStr = nextDay.toISOString().slice(0, 10)
                if (isWorkingDay(nextDayStr)) {
                  found = true
                }
                limit--
              }
              const nextWorkingStart = nextDay.toISOString().slice(0, 10)
              if (!maxDirectPredecessorNextWorkingDay || nextWorkingStart > maxDirectPredecessorNextWorkingDay) {
                maxDirectPredecessorNextWorkingDay = nextWorkingStart
              }
            }
          })
        }
        
        if (maxDirectPredecessorNextWorkingDay) {
          expectedStart = maxDirectPredecessorNextWorkingDay
        } else {
          expectedStart = getFirstWorkingDay(task.startDate)
        }
        
        let maxAncestorPredecessorNextWorkingDay = null
        const ancestors = getAncestors(wbs, task)
        ancestors.forEach(anc => {
          if (anc.predecessors) {
            const predCodes = anc.predecessors.split(',').map(s => s.trim()).filter(Boolean)
            predCodes.forEach(code => {
              const predTask = taskMap.get(code) || wbs.find(t => t.id === code)
              const predEndDate = getTaskEffectiveEndDate(wbs, predTask)
              if (predEndDate) {
                let nextDay = new Date(predEndDate)
                let found = false
                let limit = 366
                while (!found && limit > 0) {
                  nextDay.setUTCDate(nextDay.getUTCDate() + 1)
                  const nextDayStr = nextDay.toISOString().slice(0, 10)
                  if (isWorkingDay(nextDayStr)) {
                    found = true
                  }
                  limit--
                }
                const nextWorkingStart = nextDay.toISOString().slice(0, 10)
                if (!maxAncestorPredecessorNextWorkingDay || nextWorkingStart > maxAncestorPredecessorNextWorkingDay) {
                  maxAncestorPredecessorNextWorkingDay = nextWorkingStart
                }
              }
            })
          }
        })
        
        if (maxAncestorPredecessorNextWorkingDay && maxAncestorPredecessorNextWorkingDay > expectedStart) {
          expectedStart = maxAncestorPredecessorNextWorkingDay
        }
        
        const expectedEnd = addWorkingDays(expectedStart, task.duration)
        
        if (task.startDate !== expectedStart || task.endDate !== expectedEnd) {
          task.startDate = expectedStart
          task.endDate = expectedEnd
          changed = true
        }
      }
    }
    
    // Pass 2
    for (let i = wbs.length - 1; i >= 0; i--) {
      const task = wbs[i]
      if (task.isParent) {
        const descendants = wbs.filter(c => !c.isParent && c.outlineCode && task.outlineCode && c.outlineCode.startsWith(task.outlineCode + '.'))
        if (descendants.length) {
          const startDates = descendants.map(c => c.startDate).filter(Boolean)
          const endDates = descendants.map(c => c.endDate).filter(Boolean)
          if (startDates.length && endDates.length) {
            const minStart = startDates.reduce((min, d) => d < min ? d : min, startDates[0])
            const maxEnd = endDates.reduce((max, d) => d > max ? d : max, endDates[0])
            const calculatedDuration = getWorkingDaysCount(minStart, maxEnd)
            
            if (task.startDate !== minStart || task.endDate !== maxEnd || task.duration !== calculatedDuration) {
              task.startDate = minStart
              task.endDate = maxEnd
              task.duration = calculatedDuration
              changed = true
            }
          }
        }
      }
    }
  }
  
  return wbs
}

// User WBS data
const userWbs = [
  {"id":"1","wbsCode":"1","level":0,"title":"需求与原型","duration":7,"startDate":"2026-06-08","endDate":"2026-06-16","predecessors":"","resourceIds":[],"milestone":false,"progress":86,"isParent":true,"outlineCode":"1"},
  {"id":"2","wbsCode":"2","level":1,"title":"需求分析编制","duration":5,"startDate":"2026-06-08","endDate":"2026-06-12","predecessors":"","resourceIds":[2,3],"milestone":false,"progress":100,"isParent":false,"outlineCode":"1.1"},
  {"id":"3","wbsCode":"3","level":1,"title":"原型界面设计","duration":2,"startDate":"2026-06-15","endDate":"2026-06-16","predecessors":"2","resourceIds":[2],"milestone":false,"progress":50,"isParent":false,"outlineCode":"1.2"},
  {"id":"4","wbsCode":"4","level":0,"title":"核心开发与联调","duration":417,"startDate":"2026-06-18","endDate":"2028-01-21","predecessors":"1","resourceIds":[],"milestone":false,"progress":20,"isParent":true,"outlineCode":"2"},
  {"id":"5","wbsCode":"5","level":1,"title":"后端架构搭建与设计","duration":413,"startDate":"2026-06-18","endDate":"2028-01-17","predecessors":"","resourceIds":[],"milestone":false,"progress":20,"isParent":false,"outlineCode":"2.1"},
  {"id":"6","wbsCode":"6","level":1,"title":"前端页面与图表开发","duration":4,"startDate":"2028-01-18","endDate":"2028-01-21","predecessors":"5","resourceIds":[],"milestone":false,"progress":0,"isParent":false,"outlineCode":"2.2"},
  {"id":"7","wbsCode":"7","level":0,"title":"项目上线与交付","duration":3,"startDate":"2028-01-24","endDate":"2028-01-26","predecessors":"4","resourceIds":[],"milestone":true,"isParent":true,"outlineCode":"3"},
  {"id":"t_1781065972873","wbsCode":"8","level":1,"title":"新增任务规划项","duration":3,"startDate":"2028-01-24","endDate":"2028-01-26","predecessors":"","resourceIds":[],"resourceUnits":{},"milestone":false,"progress":0,"isParent":false,"outlineCode":"3.1"}
]

console.log("=== Running schedule on original user WBS ===")
const res1 = runSchedule(userWbs)
res1.forEach(t => {
  console.log(`${t.outlineCode} ${t.title}: start=${t.startDate}, end=${t.endDate}, duration=${t.duration}, isParent=${t.isParent}`)
})

console.log("\n=== Running schedule on user WBS, but with Backend Task (Task 5) duration set to 4 ===")
const userWbsFixedTask5 = JSON.parse(JSON.stringify(userWbs))
userWbsFixedTask5[4].duration = 4 // Set Task 5's duration to 4 instead of 413
userWbsFixedTask5[4].endDate = "2026-06-23" // reset end date
const res2 = runSchedule(userWbsFixedTask5)
res2.forEach(t => {
  console.log(`${t.outlineCode} ${t.title}: start=${t.startDate}, end=${t.endDate}, duration=${t.duration}, isParent=${t.isParent}`)
})
