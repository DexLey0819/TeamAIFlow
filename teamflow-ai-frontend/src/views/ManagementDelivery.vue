<template>
  <div class="management-delivery-container" style="padding: 24px; background: #f8fafc; min-height: 100vh;">
    <!-- Page Header -->
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
      <div>
        <h1 class="page-title" style="font-size: 24px; font-weight: 800; color: #1e293b; margin: 0;">项目管理</h1>
        <p class="page-subtitle" style="font-size: 14px; color: #64748b; margin: 4px 0 0;">集成 WBS、甘特图、资源库、工作日历与项目基线的一站式项目管理 center</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 12px; align-items: center;">
        <el-tag v-if="isPM" type="success" effect="dark">项目经理权限已启用</el-tag>
        <el-tag v-else type="info">成员视图（只读模式）</el-tag>
        <el-button-group>
          <el-button type="primary" :disabled="!isPM" @click="saveToLocalStorage">保存规划</el-button>
          <el-button type="info" @click="exportToHtml">导出成果报告</el-button>
        </el-button-group>
      </div>
    </div>

    <!-- Main Tabs -->
    <el-tabs v-model="activeTab" type="border-card" class="premium-tabs" style="box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05); border-radius: 12px; overflow: hidden; border: none;">
      
      <!-- 1. WBS & Gantt Sheet -->
      <el-tab-pane label="WBS 与甘特图" name="gantt">
        <div class="gantt-wrapper" style="display: flex; flex-direction: column; gap: 16px;">
          <!-- Toolbar -->
          <div v-if="isPM" class="wbs-toolbar" style="display: flex; gap: 8px;">
            <el-button size="small" type="primary" @click="addTask">新增任务</el-button>
            <el-button size="small" type="danger" :disabled="!selectedTask" @click="deleteTask">删除任务</el-button>
            <el-button size="small" @click="indentTask">缩进 (子任务)</el-button>
            <el-button size="small" @click="outdentTask">升级 (父任务)</el-button>
            <el-button size="small" type="warning" @click="handleAutoSchedule">自动排期</el-button>
            <el-button size="small" type="success" plain @click="showBaselineDialog = true">保存项目基线</el-button>
          </div>

          <div class="split-pane" style="display: flex; gap: 16px; min-height: 520px; flex-wrap: wrap;">
            <!-- Left Side: WBS Table -->
            <div class="wbs-table-panel" style="flex: 1; min-width: 550px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; overflow-x: auto;">
              <h4 style="margin: 0 0 12px; color: #334155; font-weight: 700;">WBS 任务表</h4>
              <el-table 
                :data="wbs" 
                highlight-current-row 
                border 
                size="small"
                @current-change="handleTaskSelect"
                style="width: 100%; font-size: 12px;"
              >
                <el-table-column label="WBS" prop="wbsCode" width="60" align="center" />
                <el-table-column label="任务名称" min-width="180">
                  <template #default="{ row }">
                    <span :style="{ paddingLeft: (row.level * 16) + 'px', fontWeight: row.isParent ? 'bold' : 'normal' }">
                      <span style="color: #64748b; font-size: 11px; font-weight: 500; margin-right: 6px; font-family: monospace;">{{ row.outlineCode }}</span>
                      <el-input 
                        v-if="isPM" 
                        v-model="row.title" 
                        size="small" 
                        style="width: 70%; display: inline-block; vertical-align: middle;"
                        @change="markDirty"
                      />
                      <span v-else style="vertical-align: middle;">{{ row.title }}</span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="工期(天)" width="70" align="center">
                  <template #default="{ row }">
                    <el-input-number 
                      v-if="isPM && !row.isParent" 
                      v-model="row.duration" 
                      :min="0" 
                      size="small" 
                      controls-position="right"
                      @change="handleDurationChange(row)"
                      style="width: 60px;"
                    />
                    <span v-else>{{ row.duration }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="开始日期" width="120">
                  <template #default="{ row }">
                    <el-date-picker 
                      v-if="isPM && !row.isParent" 
                      v-model="row.startDate" 
                      type="date" 
                      value-format="YYYY-MM-DD"
                      size="small" 
                      placeholder="开始"
                      @change="handleDateChange(row)"
                      style="width: 110px;"
                    />
                    <span v-else>{{ row.startDate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="完成日期" width="120">
                  <template #default="{ row }">
                    <el-date-picker 
                      v-if="isPM && !row.isParent" 
                      v-model="row.endDate" 
                      type="date" 
                      value-format="YYYY-MM-DD"
                      size="small" 
                      placeholder="结束"
                      @change="handleDateChange(row)"
                      style="width: 110px;"
                    />
                    <span v-else>{{ row.endDate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="前置任务" width="70">
                  <template #default="{ row }">
                    <el-input 
                      v-if="isPM" 
                      v-model="row.predecessors" 
                      size="small" 
                      placeholder="如: 1"
                      @change="markDirty"
                    />
                    <span v-else>{{ row.predecessors }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="分配资源" min-width="140">
                  <template #default="{ row }">
                    <div style="display: flex; align-items: center; justify-content: space-between; gap: 4px; width: 100%;">
                      <span style="font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #475569; max-width: 140px;" :title="getResourceAssignmentText(row)">
                        {{ getResourceAssignmentText(row) }}
                      </span>
                      <el-button 
                        v-if="isPM && !row.isParent" 
                        type="primary" 
                        link 
                        size="small" 
                        @click="openResourceAssignmentDialog(row)"
                        style="padding: 0; font-size: 11px;"
                      >
                        分配
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="进度" width="80" align="center">
                  <template #default="{ row }">
                    <el-input-number 
                      v-if="isPM && !row.isParent" 
                      v-model="row.progress" 
                      :min="0" 
                      :max="100" 
                      :step="10"
                      size="small" 
                      controls-position="right"
                      @change="markDirty"
                      style="width: 70px;"
                    />
                    <el-progress v-else :percentage="row.progress" size="small" />
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- Right Side: SVG Gantt Chart -->
            <div class="gantt-chart-panel" style="flex: 1; min-width: 500px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; display: flex; flex-direction: column;">
              <div class="chart-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <h4 style="margin: 0; color: #334155; font-weight: 700;">甘特图直观视角</h4>
                <div class="zoom-controls">
                  <el-radio-group v-model="zoomLevel" size="small">
                    <el-radio-button label="day">日视图</el-radio-button>
                    <el-radio-button label="week">周视图</el-radio-button>
                  </el-radio-group>
                </div>
              </div>

              <!-- Gantt SVG Canvas Container -->
              <div class="gantt-svg-scroll" style="overflow: auto; flex: 1; border: 1px solid #f1f5f9; border-radius: 6px;">
                <svg :width="ganttWidth" :height="ganttHeight" style="background: #fafafa;">
                  <!-- Timeline Header Grid -->
                  <g class="timeline-header">
                    <rect x="0" y="0" :width="ganttWidth" height="40" fill="#f8fafc" stroke="#e2e8f0" stroke-width="1" />
                    <g v-for="(col, index) in timelineCols" :key="index">
                      <line :x1="col.x" y1="0" :x2="col.x" :y2="ganttHeight" stroke="#f1f5f9" stroke-width="1" />
                      <text :x="col.x + 6" y="24" fill="#64748b" style="font-size: 11px; font-weight: 600;">{{ col.label }}</text>
                    </g>
                  </g>

                  <!-- Row grids and Task Bars -->
                  <g v-for="(row, index) in wbs" :key="row.id" class="gantt-row">
                    <!-- Row background striping -->
                    <rect x="0" :y="40 + index * 40" :width="ganttWidth" height="40" :fill="index % 2 === 0 ? '#ffffff' : '#fcfcfc'" stroke="#f1f5f9" stroke-width="1" />
                    
                    <!-- Text label -->
                    <text x="10" :y="40 + index * 40 + 24" fill="#334155" style="font-size: 11px; font-weight: bold;">{{ row.outlineCode }} {{ row.title }}</text>

                    <!-- Baseline comparison bar (if baseline exist) -->
                    <rect 
                      v-if="selectedBaseline && getBaselineTaskBar(row.id)" 
                      :x="getBaselineTaskBar(row.id).x" 
                      :y="40 + index * 40 + 26" 
                      :width="getBaselineTaskBar(row.id).width" 
                      height="8" 
                      rx="2" 
                      fill="#cbd5e1" 
                      opacity="0.7" 
                    />

                    <!-- Actual Current task bar -->
                    <!-- Milestone rendering (diamond shape) -->
                    <polygon 
                      v-if="row.duration === 0" 
                      :points="getMilestonePoints(index, row.startDate)" 
                      fill="#e11d48" 
                      stroke="#be123c"
                      stroke-width="1"
                    />
                    
                    <!-- Standard task bar -->
                    <g v-else>
                      <rect 
                        :x="getTaskBarX(row.startDate)" 
                        :y="40 + index * 40 + 8" 
                        :width="getTaskBarWidth(row.startDate, row.endDate)" 
                        height="14" 
                        rx="4" 
                        :fill="row.isParent ? '#475569' : '#0284c7'" 
                      />
                      <!-- Progress overlay -->
                      <rect 
                        :x="getTaskBarX(row.startDate)" 
                        :y="40 + index * 40 + 8" 
                        :width="getTaskBarWidth(row.startDate, row.endDate) * (row.progress / 100)" 
                        height="14" 
                        rx="4" 
                        :fill="row.isParent ? '#0f172a' : '#0369a1'" 
                      />
                      <!-- Completion percentage text next to bar -->
                      <text 
                        :x="getTaskBarX(row.startDate) + getTaskBarWidth(row.startDate, row.endDate) + 8" 
                        :y="40 + index * 40 + 20" 
                        fill="#64748b" 
                        style="font-size: 10px;"
                      >{{ row.progress }}%</text>
                    </g>
                  </g>

                  <!-- Dependency connection arrows (FS) -->
                  <g class="dependency-arrows">
                    <path 
                      v-for="(arrow, idx) in dependencyArrows" 
                      :key="idx" 
                      :d="arrow.path" 
                      fill="none" 
                      stroke="#f43f5e" 
                      stroke-width="1.5" 
                      marker-end="url(#arrow)" 
                      opacity="0.85"
                    />
                  </g>
                  
                  <!-- SVG Definitions for Markers -->
                  <defs>
                    <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                      <path d="M 0 1 L 10 5 L 0 9 z" fill="#f43f5e" />
                    </marker>
                  </defs>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 2. Resource Library tab -->
      <el-tab-pane label="资源管理库" name="resources">
        <div class="resource-panel" style="padding: 12px; background: #fff; border-radius: 8px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <h3 style="margin: 0; font-weight: 700; color: #1e293b;">项目资源库</h3>
            <el-button v-if="isPM" type="primary" size="small" @click="addResource">引入外部资源</el-button>
          </div>
          
          <el-table :data="resources" border style="width: 100%;">
            <el-table-column label="资源名称" prop="name" />
            <el-table-column label="资源类型" prop="type">
              <template #default="{ row }">
                <el-tag :type="row.type === 'MEMBER' ? 'success' : row.type === 'EQUIPMENT' ? 'warning' : 'info'">
                  {{ row.type === 'MEMBER' ? '项目成员' : row.type === 'EQUIPMENT' ? '设备资源' : '材料资源' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="费率 (时薪 / 单价)">
              <template #default="{ row }">
                <el-input-number 
                  v-if="isPM" 
                  v-model="row.rate" 
                  :min="0" 
                  size="small" 
                  @change="markDirty" 
                />
                <span v-else>${{ row.rate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button 
                  v-if="isPM"
                  type="danger" 
                  link 
                  :disabled="row.type === 'MEMBER'" 
                  @click="deleteResource(row.id)"
                >删除</el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 3. Working Calendar tab -->
      <el-tab-pane label="工作日历" name="calendar">
        <div class="calendar-panel" style="padding: 12px; background: #fff; border-radius: 8px;">
          <h3 style="margin: 0 0 16px; font-weight: 700; color: #1e293b;">工作日历设定</h3>
          
          <div style="display: flex; gap: 24px; flex-wrap: wrap;">
            <!-- Left Side: Basic & Working Hours Settings -->
            <div style="flex: 1; min-width: 320px;">
              <h4 style="margin: 0 0 12px; color: #475569;">基础设置与工作时间</h4>
              <el-form label-position="top">
                <el-form-item label="常规工作日 (周)">
                  <el-checkbox-group v-model="calendar.workDays" :disabled="!isPM" @change="markDirty">
                    <el-checkbox :label="1">周一</el-checkbox>
                    <el-checkbox :label="2">周二</el-checkbox>
                    <el-checkbox :label="3">周三</el-checkbox>
                    <el-checkbox :label="4">周四</el-checkbox>
                    <el-checkbox :label="5">周五</el-checkbox>
                    <el-checkbox :label="6" style="color:#ef4444">周六</el-checkbox>
                    <el-checkbox :label="0" style="color:#ef4444">周日</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
                
                <el-form-item label="项目单日工作时长 (小时)">
                  <el-input-number v-model="calendar.dailyWorkHours" :min="1" :max="24" :disabled="!isPM" @change="markDirty" style="width: 150px;" />
                </el-form-item>
                
                <el-form-item label="单日的项目工作时间段">
                  <div style="display: flex; flex-direction: column; gap: 8px;">
                    <div v-for="(item, idx) in calendar.workTimeIntervals" :key="idx" style="display: flex; gap: 8px; align-items: center;">
                      <el-time-picker v-model="item.start" format="HH:mm" value-format="HH:mm" placeholder="开始" size="small" :disabled="!isPM" @change="markDirty" style="width: 100px;" />
                      <span>至</span>
                      <el-time-picker v-model="item.end" format="HH:mm" value-format="HH:mm" placeholder="结束" size="small" :disabled="!isPM" @change="markDirty" style="width: 100px;" />
                      <el-button v-if="isPM" type="danger" link size="small" @click="removeTimeInterval(idx)">删除</el-button>
                    </div>
                    <el-button v-if="isPM" type="primary" plain size="small" @click="addTimeInterval" style="width: 120px; margin-top: 4px;">添加时间段</el-button>
                  </div>
                </el-form-item>
              </el-form>
            </div>

            <!-- Right Side: Special Workdays & Non-workdays -->
            <div style="flex: 1; min-width: 320px; display: flex; gap: 16px;">
              <!-- Special Non-workdays (Holidays) -->
              <div style="flex: 1;">
                <h4 style="margin: 0 0 12px; color: #475569;">特殊非工作日 (节假日)</h4>
                <div style="display: flex; flex-direction: column; gap: 8px;">
                  <div v-if="isPM" style="display: flex; gap: 8px;">
                    <el-date-picker v-model="newHoliday" type="date" value-format="YYYY-MM-DD" placeholder="选择放假日期" size="small" style="width: 140px;" />
                    <el-button type="primary" size="small" @click="addHoliday">添加</el-button>
                  </div>
                  <el-table :data="calendar.holidays.map(d => ({ date: d }))" size="small" border max-height="250px">
                    <el-table-column prop="date" label="放假日期" />
                    <el-table-column v-if="isPM" label="操作" width="80" align="center">
                      <template #default="{ row }">
                        <el-button type="danger" link size="small" @click="deleteHoliday(row.date)">移除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>

              <!-- Special Workdays (Make-up work days) -->
              <div style="flex: 1;">
                <h4 style="margin: 0 0 12px; color: #475569;">特殊工作日 (调休加班)</h4>
                <div style="display: flex; flex-direction: column; gap: 8px;">
                  <div v-if="isPM" style="display: flex; gap: 8px;">
                    <el-date-picker v-model="newSpecialWorkday" type="date" value-format="YYYY-MM-DD" placeholder="选择上班日期" size="small" style="width: 140px;" />
                    <el-button type="primary" size="small" @click="addSpecialWorkday">添加</el-button>
                  </div>
                  <el-table :data="(calendar.specialWorkDays || []).map(d => ({ date: d }))" size="small" border max-height="250px">
                    <el-table-column prop="date" label="工作日期" />
                    <el-table-column v-if="isPM" label="操作" width="80" align="center">
                      <template #default="{ row }">
                        <el-button type="danger" link size="small" @click="deleteSpecialWorkday(row.date)">移除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 4. Member Workload Analysis tab -->
      <el-tab-pane label="成员负载分析" name="workload" @click="renderWorkloadChart">
        <div class="workload-panel" style="padding: 12px; background: #fff; border-radius: 8px;">
          <h3 style="margin: 0 0 8px; font-weight: 700; color: #1e293b;">成员与资源负载分析</h3>
          <p style="font-size: 13px; color: #64748b; margin-bottom: 20px;">
            计算计划任务分配中，每人每日的总额定工时。每日负荷大于调休预设的日工作时长（当前为 {{ calendar.dailyWorkHours || 8 }} 小时）即为**超载 (Overloaded)**并呈红色警示。
          </p>
          
          <!-- Resource selector for chart -->
          <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 14px; color: #475569;">选择资源查看负载趋势：</span>
            <el-select v-model="selectedWorkloadResourceId" placeholder="请选择成员" size="small" @change="renderWorkloadChart" style="width: 180px;">
              <el-option v-for="res in resources" :key="res.id" :label="res.name" :value="res.id" />
            </el-select>
          </div>
          
          <div ref="workloadChartRef" style="width: 100%; height: 320px; margin-bottom: 24px;"></div>
          
          <!-- Workload Matrix Grid Table -->
          <h4 style="margin: 20px 0 12px; color: #334155; font-weight: 700;">资源负载工时矩阵</h4>
          <div style="overflow-x: auto; border: 1px solid #e2e8f0; border-radius: 8px;">
            <table style="width: 100%; border-collapse: collapse; font-size: 12px; min-width: 800px; text-align: center;">
              <thead>
                <tr style="background: #f8fafc; border-bottom: 1px solid #e2e8f0;">
                  <th style="padding: 10px; border-right: 1px solid #e2e8f0; text-align: left; min-width: 120px; color: #475569; font-weight: 600;">资源姓名</th>
                  <th style="padding: 10px; border-right: 1px solid #e2e8f0; color: #475569; font-weight: 600;">类型</th>
                  <th v-for="col in timelineCols" :key="col.label" style="padding: 10px; border-right: 1px solid #e2e8f0; color: #475569; font-weight: 600;">
                    {{ col.label }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="res in resources" :key="res.id" style="border-bottom: 1px solid #e2e8f0;">
                  <td style="padding: 10px; border-right: 1px solid #e2e8f0; text-align: left; font-weight: 600; color: #1e293b;">
                    {{ res.name }}
                  </td>
                  <td style="padding: 10px; border-right: 1px solid #e2e8f0;">
                    <span style="font-size: 11px; color: #64748b;">
                      {{ res.type === 'MEMBER' ? '成员' : res.type === 'EQUIPMENT' ? '设备' : '材料' }}
                    </span>
                  </td>
                  <td 
                    v-for="col in timelineCols" 
                    :key="col.label" 
                    :style="getMatrixCellStyle(calculateResourceWorkload(res.id, col))"
                    style="padding: 10px; border-right: 1px solid #e2e8f0;"
                  >
                    {{ calculateResourceWorkload(res.id, col) ? Number(calculateResourceWorkload(res.id, col).toFixed(1)) : '-' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </el-tab-pane>

      <!-- 5. Baseline & EVM Variance Analysis tab -->
      <el-tab-pane label="项目基线与偏差分析" name="baseline">
        <div class="baseline-panel" style="padding: 12px; background: #fff; border-radius: 8px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <h3 style="margin: 0; font-weight: 700; color: #1e293b;">偏差分析 (基线对比)</h3>
            <div style="display: flex; gap: 8px; align-items: center;">
              <span style="font-size: 13px; color: #64748b;">对比基线:</span>
              <el-select v-model="selectedBaseline" clearable placeholder="请选择基准线" size="small" style="width: 150px;">
                <el-option v-for="b in baselines" :key="b.id" :label="b.name" :value="b.id" />
              </el-select>
            </div>
          </div>

          <el-table :data="wbs" border size="small" style="width: 100%;">
            <el-table-column label="任务名称" prop="title" />
            <el-table-column label="基线计划时间" width="220" align="center">
              <template #default="{ row }">
                <span v-if="getBaselineTask(row.id)">{{ getBaselineTask(row.id).startDate }} ~ {{ getBaselineTask(row.id).endDate }}</span>
                <span v-else class="text-muted" style="color: #94a3b8;">无基线数据</span>
              </template>
            </el-table-column>
            <el-table-column label="当前实际时间" width="220" align="center">
              <template #default="{ row }">
                <span>{{ row.startDate }} ~ {{ row.endDate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="工期偏差 (天)" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getScheduleVariance(row) > 0 ? 'danger' : getScheduleVariance(row) < 0 ? 'success' : 'info'">
                  {{ getScheduleVariance(row) > 0 ? '+' : '' }}{{ getScheduleVariance(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="基线成本计划" width="100" align="center">
              <template #default="{ row }">
                <span>${{ getBaselineTaskCost(row.id) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="当前预算估计" width="100" align="center">
              <template #default="{ row }">
                <span>${{ calculateTaskCost(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="成本偏差" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="calculateCostVariance(row) > 0 ? 'danger' : calculateCostVariance(row) < 0 ? 'success' : 'info'">
                  ${{ calculateCostVariance(row) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 6. AI Scheduler Assistant -->
      <el-tab-pane label="AI 计划助理" name="ai">
        <div class="ai-panel" style="padding: 12px; background: #fff; border-radius: 8px; display: flex; gap: 16px; flex-direction: column;">
          <h3 style="margin: 0; font-weight: 700; color: #1e293b;">GLM 计划进度诊断智能体</h3>
          <p style="font-size: 13px; color: #64748b; margin: 0;">基于当前项目 WBS 树、前置依赖、实际工时与基线对比，自动分析关键路径 (Critical Path) 及排程冲突。</p>
          
          <div style="display: flex; gap: 12px;">
            <el-button type="primary" :loading="aiLoading" @click="runAiDiagnosis">执行计划分析诊断</el-button>
          </div>

          <div v-if="aiResult" class="ai-diagnosis-result" style="border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; background: #f8fafc; font-size: 14px; line-height: 1.6; color: #334155;">
            <div style="display: flex; align-items: center; gap: 8px; font-weight: bold; margin-bottom: 12px;">
              <el-tag type="danger">AI 诊断报告</el-tag>
              <span>关键路径与进度偏离建议：</span>
            </div>
            <div style="white-space: pre-wrap;" v-html="aiResult"></div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 7. Plan Review & Discussion Tab -->
      <el-tab-pane label="计划审核与讨论" name="planReview">
        <div style="display: flex; gap: 24px; min-height: 500px; flex-wrap: wrap;">
          <!-- Left Side: Status & Reviews -->
          <div style="flex: 1.5; min-width: 380px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; display: flex; flex-direction: column; gap: 16px;">
            <h3 style="margin: 0; font-weight: 700; color: #1e293b;">项目计划审核状态</h3>
            
            <div v-if="deliverySection" class="status-summary" style="display: flex; flex-direction: column; gap: 12px;">
              <el-alert
                :title="`计划当前状态：${statusTextMap[deliverySection.status] || deliverySection.status}`"
                :type="statusTypeMap[deliverySection.status]"
                :closable="false"
                show-icon
              />
              
              <!-- All Approved Status Card -->
              <el-alert
                v-if="deliverySection.allApproved"
                title="项目全员已审批通过该计划！"
                type="success"
                description="项目所有评审人均已签署同意意见。点击下方按钮即可一键将当前的 WBS 列表、甘特图代码与 AI 排程诊断报告导入项目文档中。"
                :closable="false"
                show-icon
              />
              <el-alert
                v-else-if="deliverySection.status === 'REVIEWING'"
                title="正在等待项目评审人审核..."
                type="warning"
                description="项目经理需要等待所有成员（评审人）发表通过意见。当全员通过后即可一键导入文档。"
                :closable="false"
                show-icon
              />

              <!-- Import WBS to Document Button -->
              <div v-if="isPM" style="margin-top: 8px;">
                <el-button 
                  type="success" 
                  size="large"
                  style="width: 100%; font-weight: bold;"
                  :disabled="!deliverySection.allApproved"
                  @click="importWbsToDocument"
                >
                  一键导入 WBS 与甘特图到文档
                </el-button>
                <p style="font-size: 12px; color: #64748b; margin-top: 4px; text-align: center;">
                  (当全员审批通过后激活。导入后，WBS 数据与 PlantUML 甘特图将自动渲染进项目文档的《项目管理》章节中)
                </p>
              </div>

              <!-- PM Submit Plan for Review -->
              <div v-if="isPM && (deliverySection.status === 'DRAFT' || deliverySection.status === 'EMPTY' || deliverySection.status === 'REJECTED')">
                <el-button type="primary" size="large" style="width: 100%;" @click="submitDeliverySection">
                  提交计划供全员审核
                </el-button>
              </div>
            </div>

            <!-- Review Actions for Project Members -->
            <div v-if="deliverySection && deliverySection.status === 'REVIEWING' && canReview" class="review-action-panel" style="border-top: 1px solid #e2e8f0; padding-top: 16px; margin-top: 16px;">
              <h4 style="margin: 0 0 12px; color: #334155;">计划审核操作</h4>
              <el-form label-position="top">
                <el-form-item label="审核评语 / 修改意见">
                  <el-input
                    v-model="reviewForm.comment"
                    type="textarea"
                    :rows="4"
                    placeholder="请输入对该项目计划、排程、分工和工期的意见..."
                  />
                </el-form-item>
              </el-form>
              <div style="display: flex; gap: 12px; justify-content: flex-end;">
                <el-button type="success" @click="handleDeliveryReview('APPROVED')">审核通过</el-button>
                <el-button type="danger" @click="handleDeliveryReview('REJECTED')">打回修改</el-button>
              </div>
            </div>

            <!-- Historical Reviews List -->
            <div class="reviews-history" style="border-top: 1px solid #e2e8f0; padding-top: 16px; margin-top: 16px; flex: 1; overflow-y: auto;">
              <h4 style="margin: 0 0 12px; color: #334155;">历史审批意见</h4>
              <div v-if="deliveryReviews.length" style="display: flex; flex-direction: column; gap: 10px;">
                <div v-for="rev in deliveryReviews" :key="rev.id" class="review-item" style="background: #f8fafc; padding: 12px; border-radius: 6px; border: 1px solid #e2e8f0;">
                  <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 6px;">
                    <span style="font-weight: 600; color: #475569;">{{ rev.reviewerName }}</span>
                    <span style="color: #94a3b8;">{{ formatTime(rev.createTime) }}</span>
                  </div>
                  <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
                    <el-tag size="small" :type="rev.reviewResult === 'APPROVED' ? 'success' : 'danger'">
                      {{ rev.reviewResult === 'APPROVED' ? '同意' : '驳回' }}
                    </el-tag>
                  </div>
                  <div style="font-size: 13px; color: #1e293b;">{{ rev.reviewComment || '（无评语）' }}</div>
                </div>
              </div>
              <div v-else style="color: #94a3b8; font-size: 13px; text-align: center; padding: 20px 0;">
                暂无审批记录
              </div>
            </div>
          </div>

          <!-- Right Side: Discussion Feed -->
          <div style="flex: 1; min-width: 320px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; display: flex; flex-direction: column;">
            <h3 style="margin: 0 0 16px; font-weight: 700; color: #1e293b;">计划讨论留言区</h3>
            
            <div style="flex: 1; overflow-y: auto; max-height: 400px; margin-bottom: 16px; display: flex; flex-direction: column; gap: 12px;">
              <div v-if="deliveryComments.length" style="display: flex; flex-direction: column; gap: 10px;">
                <div v-for="c in deliveryComments" :key="c.id" class="comment-item" style="background: #f8fafc; padding: 12px; border-radius: 6px; border: 1px solid #e2e8f0;">
                  <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 4px;">
                    <span style="font-weight: 600; color: #475569;">{{ c.realName }}</span>
                    <span style="color: #94a3b8;">{{ formatTime(c.createTime) }}</span>
                  </div>
                  <div style="font-size: 13px; color: #1e293b; white-space: pre-wrap;">{{ c.commentText }}</div>
                </div>
              </div>
              <div v-else style="color: #94a3b8; font-size: 13px; text-align: center; padding: 40px 0;">
                暂无讨论留言，发表第一条留言吧
              </div>
            </div>

            <div v-if="deliverySection" class="comment-form" style="border-top: 1px solid #e2e8f0; padding-top: 16px;">
              <el-input
                v-model="activeCommentText"
                type="textarea"
                :rows="3"
                placeholder="在此输入关于计划调整、进度配合的交流内容..."
              />
              <el-button
                type="primary"
                plain
                style="margin-top: 8px;"
                :disabled="!activeCommentText"
                @click="submitDeliveryComment"
              >
                发表留言
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Save Baseline Dialog -->
    <el-dialog v-model="showBaselineDialog" title="保存项目基线" width="400px">
      <el-form>
        <el-form-item label="基线名称">
          <el-input v-model="baselineName" placeholder="如: 计划大纲基准 V1.0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBaselineDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBaseline">确认保存</el-button>
      </template>
    </el-dialog>

    <!-- Resource Assignment units dialog -->
    <el-dialog v-model="assignmentDialogVisible" title="配置资源分配与参与比例" width="520px" destroy-on-close>
      <div v-if="editingTaskForAssignment" style="display: flex; flex-direction: column; gap: 14px;">
        <div style="font-weight: 700; font-size: 14px; color: #1e293b; background: #f1f5f9; padding: 8px 12px; border-radius: 6px;">
          任务：{{ editingTaskForAssignment.title }}
        </div>
        
        <el-table :data="resourcesForAssignment" border size="small" style="width: 100%; max-height: 300px; overflow-y: auto;">
          <el-table-column label="指派" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.assigned" />
            </template>
          </el-table-column>
          <el-table-column label="资源名称" prop="name" />
          <el-table-column label="类型" width="70" align="center">
            <template #default="{ row }">
              <span style="font-size: 11px; color: #64748b;">
                {{ row.type === 'MEMBER' ? '成员' : row.type === 'EQUIPMENT' ? '设备' : '材料' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="参与比(%)" width="130" align="center">
            <template #default="{ row }">
              <el-input-number 
                v-model="row.units" 
                :min="1" 
                :max="100" 
                :step="10" 
                size="small" 
                controls-position="right"
                :disabled="!row.assigned" 
                style="width: 90px;" 
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="assignmentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveResourceAssignments">确认分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { listMembers, projectDetail, saveProjectWbs } from '../api/project'
import { useUserStore } from '../stores/user'
import { mySectionPermissions } from '../api/permission'
import {
  projectSections,
  sectionDetail,
  saveSectionContent,
  submitSection,
  reviewSection,
  commentSection,
  sectionComments,
  sectionReviews
} from '../api/section'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const userStore = useUserStore()
let loadRequestId = 0
let membershipRequestId = 0
let deliverySectionRequestId = 0
let aiDiagnosisRequestId = 0

const activeTab = ref('gantt')
const isPM = ref(false)
const selectedTask = ref(null)
const isDirty = ref(false)
const zoomLevel = ref('day')

// Dialog states
const showBaselineDialog = ref(false)
const baselineName = ref('')
const assignmentDialogVisible = ref(false)

// Plan Review & Discussion states
const deliverySection = ref(null)
const deliveryComments = ref([])
const deliveryReviews = ref([])
const reviewForm = ref({ comment: '' })
const activeCommentText = ref('')
const canReview = ref(false)

const statusTextMap = {
  EMPTY: '未开始',
  DRAFT: '草稿',
  REVIEWING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已退回'
}

const statusTypeMap = {
  APPROVED: 'success',
  REVIEWING: 'warning',
  REJECTED: 'danger',
  DRAFT: 'info',
  EMPTY: 'info'
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

// WBS Task, Resource, Calendar & Baseline states
const wbs = ref([])
const resources = ref([])
const createDefaultCalendar = () => ({
  workDays: [1, 2, 3, 4, 5],
  holidays: [],
  specialWorkDays: [],
  workTimeIntervals: [
    { start: '09:00', end: '12:00' },
    { start: '13:00', end: '18:00' }
  ],
  dailyWorkHours: 8
})

const calendar = ref(createDefaultCalendar())
const baselines = ref([])
const selectedBaseline = ref(null)

// AI Assistant states
const aiLoading = ref(false)
const aiResult = ref('')
const newHoliday = ref('')
const newSpecialWorkday = ref('')
const selectedWorkloadResourceId = ref('')
const editingTaskForAssignment = ref(null)
const resourcesForAssignment = ref([])

const markDirty = () => {
  isDirty.value = true
}

// Check role
const checkMembership = async () => {
  const currentProjectId = projectId.value
  const requestId = ++membershipRequestId
  if (!currentProjectId) {
    isPM.value = false
    return
  }
  try {
    const members = await listMembers(currentProjectId)
    if (requestId !== membershipRequestId || projectId.value !== currentProjectId) return
    const currentMember = members.find(m => m.userId === userStore.user?.id)
    isPM.value = currentMember && currentMember.memberRole === 'PROJECT_MANAGER'
  } catch (error) {
    if (requestId === membershipRequestId && projectId.value === currentProjectId) {
      isPM.value = false
    }
  }
}

// WBS selection
const handleTaskSelect = (val) => {
  selectedTask.value = val
}

const getResourceNames = (ids) => {
  if (!ids || !ids.length) return '未指定'
  return ids.map(id => {
    const res = resources.value.find(r => r.id === id)
    return res ? res.name : id
  }).join(', ')
}

const getResourceAssignmentText = (row) => {
  if (row.isParent) return '-'
  if (!row.resourceIds || !row.resourceIds.length) return '未指派'
  const units = row.resourceUnits || {}
  return row.resourceIds.map(id => {
    const res = resources.value.find(r => r.id === id)
    if (!res) return id
    const pct = units[id] !== undefined ? units[id] : 100
    return `${res.name} (${pct}%)`
  }).join(', ')
}

const openResourceAssignmentDialog = (row) => {
  editingTaskForAssignment.value = row
  const units = row.resourceUnits || {}
  
  resourcesForAssignment.value = resources.value.map(res => {
    const assigned = (row.resourceIds || []).includes(res.id)
    const u = units[res.id] !== undefined ? units[res.id] : 100
    return {
      id: res.id,
      name: res.name,
      type: res.type,
      assigned,
      units: u
    }
  })
  assignmentDialogVisible.value = true
}

const saveResourceAssignments = () => {
  if (!editingTaskForAssignment.value) return
  
  const assigned = resourcesForAssignment.value.filter(r => r.assigned)
  editingTaskForAssignment.value.resourceIds = assigned.map(r => r.id)
  
  const units = {}
  assigned.forEach(r => {
    units[r.id] = r.units
  })
  editingTaskForAssignment.value.resourceUnits = units
  
  assignmentDialogVisible.value = false
  markDirty()
  ElMessage.success('资源分配与比例更新成功')
}

// Timezone-safe UTC Date Parser
const parseUTCDate = (dateStr) => {
  if (!dateStr) return null
  const parts = dateStr.split('-')
  if (parts.length !== 3) return new Date(dateStr)
  const year = parseInt(parts[0], 10)
  const month = parseInt(parts[1], 10) - 1
  const day = parseInt(parts[2], 10)
  return new Date(Date.UTC(year, month, day))
}

// Working Day Check
const isWorkingDay = (dateStr) => {
  if (!dateStr) return false
  const date = parseUTCDate(dateStr)
  if (!date) return false
  const dayOfWeek = date.getUTCDay()
  if (calendar.value.specialWorkDays && calendar.value.specialWorkDays.includes(dateStr)) {
    return true
  }
  if (calendar.value.holidays && calendar.value.holidays.includes(dateStr)) {
    return false
  }
  return calendar.value.workDays.includes(dayOfWeek)
}

const getWorkingDaysCount = (startDateStr, endDateStr) => {
  if (!startDateStr || !endDateStr) return 0
  let start = parseUTCDate(startDateStr)
  let end = parseUTCDate(endDateStr)
  if (!start || !end || end < start) return 0
  let workDaysCount = 0
  let temp = new Date(start.getTime())
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
  let date = parseUTCDate(dateStr)
  if (!date) return dateStr
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
  let date = parseUTCDate(currentStart)
  if (!date) return currentStart
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

const getAncestors = (task) => {
  const ancestors = []
  let currentLevel = task.level
  const idx = wbs.value.findIndex(t => String(t.id) === String(task.id))
  if (idx === -1) return ancestors
  for (let i = idx - 1; i >= 0; i--) {
    const t = wbs.value[i]
    if (t.level < currentLevel) {
      ancestors.push(t)
      currentLevel = t.level
    }
  }
  return ancestors
}

const adjustPredecessorsOnDelete = (deletedIdx) => {
  const deletedWbs = String(deletedIdx + 1)
  wbs.value.forEach(task => {
    if (task.predecessors) {
      const preds = task.predecessors.split(',').map(s => s.trim()).filter(Boolean)
      const newPreds = preds.map(p => {
        if (p === deletedWbs) return null
        const pNum = Number(p)
        if (!isNaN(pNum) && pNum > deletedIdx + 1) {
          return String(pNum - 1)
        }
        return p
      }).filter(Boolean)
      task.predecessors = newPreds.join(', ')
    }
  })
}

const resetDeliveryState = () => {
  deliverySection.value = null
  deliveryComments.value = []
  deliveryReviews.value = []
  reviewForm.value.comment = ''
  activeCommentText.value = ''
  canReview.value = false
}

const resetProjectState = () => {
  aiDiagnosisRequestId++
  wbs.value = []
  resources.value = []
  calendar.value = createDefaultCalendar()
  baselines.value = []
  selectedBaseline.value = null
  selectedTask.value = null
  isDirty.value = false
  showBaselineDialog.value = false
  baselineName.value = ''
  assignmentDialogVisible.value = false
  editingTaskForAssignment.value = null
  resourcesForAssignment.value = []
  aiLoading.value = false
  aiResult.value = ''
  newHoliday.value = ''
  newSpecialWorkday.value = ''
  selectedWorkloadResourceId.value = ''
  resetDeliveryState()
}

// Initial default WBS loaded from database or localStorage fallback
const loadData = async () => {
  const currentProjectId = projectId.value
  const requestId = ++loadRequestId
  resetProjectState()
  if (!currentProjectId) return

  let data = null
  let project = null
  try {
    // 1. Try loading from backend database
    project = await projectDetail(currentProjectId)
    if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
    if (project && project.wbsData) {
      data = JSON.parse(project.wbsData)
    }
  } catch (err) {
    console.error('Failed to load WBS data from database', err)
  }
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return

  // 2. Fallback to LocalStorage
  if (!data) {
    const saved = localStorage.getItem(`teamflow_wbs_${currentProjectId}`)
    if (saved) {
      try {
        data = JSON.parse(saved)
      } catch (e) {
        console.error(e)
      }
    }
  }
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return

  if (data) {
    wbs.value = data.wbs || []
    updateWbsCodes()
    resources.value = data.resources || []
    calendar.value = data.calendar || createDefaultCalendar()
    baselines.value = data.baselines || []
    
    // Ensure defaults
    if (!calendar.value.specialWorkDays) calendar.value.specialWorkDays = []
    if (!calendar.value.workTimeIntervals) {
      calendar.value.workTimeIntervals = [
        { start: '09:00', end: '12:00' },
        { start: '13:00', end: '18:00' }
      ]
    }
    if (!calendar.value.dailyWorkHours) calendar.value.dailyWorkHours = 8
    
    if (resources.value.length) {
      selectedWorkloadResourceId.value = resources.value[0].id
    }
  } else {
    // Generate default template
    wbs.value = [
      { id: '1', wbsCode: '1', level: 0, title: '阶段一：需求与原型', duration: 5, startDate: '2026-06-10', endDate: '2026-06-16', predecessors: '', resourceIds: [], resourceUnits: {}, milestone: false, progress: 80, isParent: true },
      { id: '2', wbsCode: '2', level: 1, title: '需求分析编制', duration: 3, startDate: '2026-06-10', endDate: '2026-06-12', predecessors: '', resourceIds: [], resourceUnits: {}, milestone: false, progress: 100, isParent: false },
      { id: '3', wbsCode: '3', level: 1, title: '原型界面设计', duration: 2, startDate: '2026-06-15', endDate: '2026-06-16', predecessors: '2', resourceIds: [], resourceUnits: {}, milestone: false, progress: 50, isParent: false },
      { id: '4', wbsCode: '4', level: 0, title: '阶段二：核心开发与联调', duration: 8, startDate: '2026-06-17', endDate: '2026-06-26', predecessors: '1', resourceIds: [], resourceUnits: {}, milestone: false, progress: 10, isParent: true },
      { id: '5', wbsCode: '5', level: 1, title: '后端架构搭建与设计', duration: 4, startDate: '2026-06-17', endDate: '2026-06-22', predecessors: '', resourceIds: [], resourceUnits: {}, milestone: false, progress: 20, isParent: false },
      { id: '6', wbsCode: '6', level: 1, title: '前端页面与图表开发', duration: 4, startDate: '2026-06-23', endDate: '2026-06-26', predecessors: '5', resourceIds: [], resourceUnits: {}, milestone: false, progress: 0, isParent: false },
      { id: '7', wbsCode: '7', level: 0, title: '项目上线与交付', duration: 0, startDate: '2026-06-29', endDate: '2026-06-29', predecessors: '4', resourceIds: [], resourceUnits: {}, milestone: true, progress: 0, isParent: false }
    ]
    
    calendar.value = createDefaultCalendar()
  }
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return

  // Fetch project members list
  try {
    const members = await listMembers(currentProjectId)
    if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
    resources.value = members.map(m => ({
      id: m.userId,
      name: m.realName,
      type: 'MEMBER',
      rate: m.memberRole === 'PROJECT_MANAGER' ? 120 : 80
    }))
    resources.value.push({ id: 'eq-1', name: '云主机计算集群', type: 'EQUIPMENT', rate: 25 })
    resources.value.push({ id: 'mat-1', name: 'GLM-AI 分析接口流量', type: 'MATERIAL', rate: 10 })

    if (resources.value.length) {
      selectedWorkloadResourceId.value = resources.value[0].id
    }
    updateWbsCodes()

    // If the database was empty, silently sync the newly loaded/created WBS data
    if (!project || !project.wbsData) {
      await silentSaveToBackend(currentProjectId)
    }
  } catch (err) {
    if (requestId === loadRequestId && projectId.value === currentProjectId) {
      console.error('Failed to load project members for WBS resources', err)
    }
  }
}

const saveToLocalStorage = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return false
  const payload = {
    wbs: wbs.value,
    resources: resources.value,
    calendar: calendar.value,
    baselines: baselines.value
  }
  const payloadStr = JSON.stringify(payload)
  
  // 1. Keep LocalStorage as a local backup
  localStorage.setItem(`teamflow_wbs_${currentProjectId}`, payloadStr)
  
  // 2. Sync to backend MySQL database
  try {
    await saveProjectWbs(currentProjectId, payloadStr)
    if (projectId.value !== currentProjectId) return false
    isDirty.value = false
    ElMessage.success('项目管理计划保存并云端同步成功')
    return true
  } catch (err) {
    console.error('Failed to sync project plan to backend', err)
    if (projectId.value === currentProjectId) {
      ElMessage.warning('计划已保存到浏览器本地，但同步到云端失败，请检查网络')
      return true
    }
    return false
  }
}

const silentSaveToBackend = async (capturedProjectId = null) => {
  const currentProjectId = capturedProjectId || projectId.value
  if (!currentProjectId) return false
  const payload = {
    wbs: wbs.value,
    resources: resources.value,
    calendar: calendar.value,
    baselines: baselines.value
  }
  const payloadStr = JSON.stringify(payload)
  localStorage.setItem(`teamflow_wbs_${currentProjectId}`, payloadStr)
  try {
    await saveProjectWbs(currentProjectId, payloadStr)
    return projectId.value === currentProjectId
  } catch (err) {
    console.error('Failed to silently sync WBS to backend', err)
    return false
  }
}

// Helper to calculate effective end date of a task (resolves parent summary task boundaries recursively using only leaf tasks)
const getTaskEffectiveEndDate = (task) => {
  if (!task) return ''
  if (!task.isParent) return task.endDate || ''
  
  // Find all descendant leaf tasks (any level deeper)
  const descendants = wbs.value.filter(c => !c.isParent && c.outlineCode && task.outlineCode && c.outlineCode.startsWith(task.outlineCode + '.'))
  if (!descendants.length) return task.endDate || ''
  
  const endDates = descendants.map(c => c.endDate).filter(Boolean)
  if (!endDates.length) return task.endDate || ''
  
  return endDates.reduce((max, d) => d > max ? d : max, endDates[0])
}

// Auto Scheduling Engine
const handleAutoSchedule = () => {
  if (!wbs.value.length) {
    console.log('[AutoSchedule] WBS is empty')
    return
  }
  
  console.log('[AutoSchedule] Starting auto-schedule. Task count:', wbs.value.length)
  const taskMap = new Map(wbs.value.map(t => [t.wbsCode, t]))
  
  let changed = true
  let iterations = 0
  const maxIterations = 100
  
  while (changed && iterations < maxIterations) {
    changed = false
    iterations++
    console.log(`[AutoSchedule] Iteration ${iterations} started`)
    
    // Pass 1: Update all leaf tasks based on predecessors
    for (let i = 0; i < wbs.value.length; i++) {
      const task = wbs.value[i]
      if (!task.isParent) {
        let expectedStart = ''
        console.log(`[AutoSchedule][Pass 1] Checking leaf task: ${task.outlineCode} (Row ${task.wbsCode}, Title: ${task.title})`)
        
        // 1. Resolve direct predecessors if they exist (ignore manual date, ASAP scheduling)
        let maxDirectPredecessorNextWorkingDay = null
        if (task.predecessors) {
          const predCodes = task.predecessors.split(',').map(s => s.trim()).filter(Boolean)
          console.log(`[AutoSchedule][Pass 1] Direct predecessors found:`, predCodes)
          predCodes.forEach(code => {
            const predTask = taskMap.get(code) || Array.from(taskMap.values()).find(t => String(t.id) === String(code) || String(t.wbsCode) === String(code))
            const predEndDate = getTaskEffectiveEndDate(predTask)
            console.log(`[AutoSchedule][Pass 1] Predecessor Row ${code} effective end date: ${predEndDate}`)
            if (predEndDate) {
              let nextDay = parseUTCDate(predEndDate)
              if (nextDay) {
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
                console.log(`[AutoSchedule][Pass 1] Predecessor Row ${code} next working day start: ${nextWorkingStart}`)
                if (!maxDirectPredecessorNextWorkingDay || nextWorkingStart > maxDirectPredecessorNextWorkingDay) {
                  maxDirectPredecessorNextWorkingDay = nextWorkingStart
                }
              }
            }
          })
        }
        
        if (maxDirectPredecessorNextWorkingDay) {
          expectedStart = maxDirectPredecessorNextWorkingDay
          console.log(`[AutoSchedule][Pass 1] Direct predecessor next working day resolves to: ${expectedStart}`)
        } else {
          // If no direct predecessors, use current start date aligned to working day
          expectedStart = getFirstWorkingDay(task.startDate)
          console.log(`[AutoSchedule][Pass 1] No direct predecessors. Start date aligned to working day: ${expectedStart}`)
        }
        
        // 2. Resolve ancestor predecessor constraints (Start No Earlier Than / Align with parent's dependencies)
        let maxAncestorPredecessorNextWorkingDay = null
        const ancestors = getAncestors(task)
        console.log(`[AutoSchedule][Pass 1] Ancestors found:`, ancestors.map(a => `${a.outlineCode} (Row ${a.wbsCode})`))
        ancestors.forEach(anc => {
          if (anc.predecessors) {
            const predCodes = anc.predecessors.split(',').map(s => s.trim()).filter(Boolean)
            console.log(`[AutoSchedule][Pass 1] Ancestor Row ${anc.wbsCode} predecessors:`, predCodes)
            predCodes.forEach(code => {
              const predTask = taskMap.get(code) || Array.from(taskMap.values()).find(t => String(t.id) === String(code) || String(t.wbsCode) === String(code))
              const predEndDate = getTaskEffectiveEndDate(predTask)
              console.log(`[AutoSchedule][Pass 1] Ancestor predecessor Row ${code} effective end date: ${predEndDate}`)
              if (predEndDate) {
                let nextDay = parseUTCDate(predEndDate)
                if (nextDay) {
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
                  console.log(`[AutoSchedule][Pass 1] Ancestor predecessor Row ${code} next working day start: ${nextWorkingStart}`)
                  if (!maxAncestorPredecessorNextWorkingDay || nextWorkingStart > maxAncestorPredecessorNextWorkingDay) {
                    maxAncestorPredecessorNextWorkingDay = nextWorkingStart
                  }
                }
              }
            })
          }
        })
        
        if (maxAncestorPredecessorNextWorkingDay) {
          console.log(`[AutoSchedule][Pass 1] Max ancestor predecessor next working day is: ${maxAncestorPredecessorNextWorkingDay}`)
          if (!task.predecessors) {
            // No direct predecessors: align exactly with parent's start constraint (both forward and backward)
            expectedStart = maxAncestorPredecessorNextWorkingDay
            console.log(`[AutoSchedule][Pass 1] Aligned directly with parent constraint: ${expectedStart}`)
          } else if (maxAncestorPredecessorNextWorkingDay > expectedStart) {
            // Has direct predecessors: parent start constraint acts as a "Start No Earlier Than" boundary
            expectedStart = maxAncestorPredecessorNextWorkingDay
            console.log(`[AutoSchedule][Pass 1] Shifted forward to parent constraint: ${expectedStart}`)
          }
        }
        
        const expectedEnd = addWorkingDays(expectedStart, task.duration)
        console.log(`[AutoSchedule][Pass 1] expectedStart = ${expectedStart}, expectedEnd = ${expectedEnd}`)
        
        if (task.startDate !== expectedStart || task.endDate !== expectedEnd) {
          console.log(`[AutoSchedule][Pass 1] Shifted Task ${task.wbsCode} from ${task.startDate} to ${expectedStart}`)
          task.startDate = expectedStart
          task.endDate = expectedEnd
          changed = true
        }
      }
    }
    
    // Pass 2: Recalculate parent task boundaries from bottom to top using all leaf descendants
    for (let i = wbs.value.length - 1; i >= 0; i--) {
      const task = wbs.value[i]
      if (task.isParent) {
        console.log(`[AutoSchedule][Pass 2] Recalculating parent task: ${task.outlineCode} (Row ${task.wbsCode}, Title: ${task.title})`)
        if (task.milestone) {
          console.log(`[AutoSchedule][Pass 2] Resetting milestone flag to false for parent task Row ${task.wbsCode}`)
          task.milestone = false
        }
        const descendants = wbs.value.filter(c => !c.isParent && c.outlineCode && task.outlineCode && c.outlineCode.startsWith(task.outlineCode + '.'))
        console.log(`[AutoSchedule][Pass 2] Descendants for Row ${task.wbsCode}:`, descendants.map(d => `${d.outlineCode} (Row ${d.wbsCode})`))
        if (descendants.length) {
          const startDates = descendants.map(c => c.startDate).filter(Boolean)
          const endDates = descendants.map(c => c.endDate).filter(Boolean)
          if (startDates.length && endDates.length) {
            const minStart = startDates.reduce((min, d) => d < min ? d : min, startDates[0])
            const maxEnd = endDates.reduce((max, d) => d > max ? d : max, endDates[0])
            const calculatedDuration = getWorkingDaysCount(minStart, maxEnd)
            console.log(`[AutoSchedule][Pass 2] Row ${task.wbsCode} calculated minStart = ${minStart}, maxEnd = ${maxEnd}, duration = ${calculatedDuration}`)
            
            if (task.startDate !== minStart || task.endDate !== maxEnd || task.duration !== calculatedDuration) {
              console.log(`[AutoSchedule][Pass 2] Updating parent Task ${task.wbsCode} to start=${minStart}, end=${maxEnd}, duration=${calculatedDuration}`)
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
  
  console.log('[AutoSchedule] Auto-schedule completed. Iterations:', iterations, 'Changed:', changed)
  
  markDirty()
  if (iterations >= maxIterations) {
    ElMessage.warning('检测到循环前置依赖，排程计算已截断，请检查任务前置关系。')
  } else {
    ElMessage.success('自动排期和前置依赖计算已完成')
  }
}

const handleDurationChange = (row) => {
  markDirty()
  if (row.startDate && row.duration !== null) {
    row.endDate = addWorkingDays(row.startDate, row.duration)
  }
}

const handleDateChange = (row) => {
  markDirty()
  if (row.startDate && row.endDate) {
    const start = parseUTCDate(row.startDate)
    const end = parseUTCDate(row.endDate)
    if (start && end && end < start) {
      row.endDate = row.startDate
    }
    row.duration = getWorkingDaysCount(row.startDate, row.endDate)
  }
}

// Indent & Outdent task level for WBS hierarchy
const indentTask = () => {
  if (!selectedTask.value) return
  const idx = wbs.value.indexOf(selectedTask.value)
  if (idx > 0) {
    const prevTask = wbs.value[idx - 1]
    if (selectedTask.value.level <= prevTask.level) {
      selectedTask.value.level++
      prevTask.isParent = true
      updateWbsCodes()
      markDirty()
    }
  }
}

const outdentTask = () => {
  if (!selectedTask.value) return
  if (selectedTask.value.level > 0) {
    selectedTask.value.level--
    updateWbsCodes()
    markDirty()
  }
}

const updateWbsCodes = () => {
  let counters = [0, 0, 0, 0, 0]
  wbs.value.forEach((task, idx) => {
    task.wbsCode = String(idx + 1)
    
    const level = task.level
    counters[level]++
    for (let i = level + 1; i < counters.length; i++) {
      counters[i] = 0
    }
    
    let outline = ''
    for (let i = 0; i <= level; i++) {
      outline += (i === 0 ? '' : '.') + counters[i]
    }
    task.outlineCode = outline
    
    task.isParent = idx < wbs.value.length - 1 && wbs.value[idx + 1].level > task.level
  })
}

// Add / Delete tasks
const addTask = () => {
  const newId = 't_' + Date.now()
  const lastTask = wbs.value[wbs.value.length - 1]
  const startD = lastTask ? lastTask.endDate : new Date().toISOString().slice(0, 10)
  const newRow = {
    id: newId,
    wbsCode: '',
    level: lastTask ? lastTask.level : 0,
    title: '新增任务规划项',
    duration: 3,
    startDate: startD,
    endDate: addWorkingDays(startD, 3),
    predecessors: '',
    resourceIds: [],
    resourceUnits: {},
    milestone: false,
    progress: 0,
    isParent: false
  }
  wbs.value.push(newRow)
  updateWbsCodes()
  markDirty()
}

const deleteTask = () => {
  const currentProjectId = projectId.value
  const taskToDelete = selectedTask.value
  if (!currentProjectId || !taskToDelete) return
  ElMessageBox.confirm('确定删除该任务及其子任务？', '提示', { type: 'warning' }).then(() => {
    if (projectId.value !== currentProjectId) return
    const idx = wbs.value.indexOf(taskToDelete)
    if (idx < 0) return
    adjustPredecessorsOnDelete(idx)
    wbs.value.splice(idx, 1)
    updateWbsCodes()
    if (selectedTask.value === taskToDelete) {
      selectedTask.value = null
    }
    markDirty()
  }).catch(() => {})
}

// Resources management
const addResource = () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  ElMessageBox.prompt('请输入资源名称', '新增设备/材料资源', {
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    if (projectId.value !== currentProjectId) return
    if (value) {
      resources.value.push({
        id: 'res_' + Date.now(),
        name: value,
        type: 'EQUIPMENT',
        rate: 50
      })
      markDirty()
    }
  }).catch(() => {})
}

const deleteResource = (id) => {
  resources.value = resources.value.filter(r => r.id !== id)
  markDirty()
}

// Holiday management
const addHoliday = () => {
  if (newHoliday.value) {
    if (!calendar.value.holidays) calendar.value.holidays = []
    if (!calendar.value.holidays.includes(newHoliday.value)) {
      calendar.value.holidays.push(newHoliday.value)
      calendar.value.holidays.sort()
      newHoliday.value = ''
      markDirty()
    }
  }
}

const deleteHoliday = (date) => {
  calendar.value.holidays = calendar.value.holidays.filter(d => d !== date)
  markDirty()
}

// Special workday management
const addSpecialWorkday = () => {
  if (newSpecialWorkday.value) {
    if (!calendar.value.specialWorkDays) calendar.value.specialWorkDays = []
    if (!calendar.value.specialWorkDays.includes(newSpecialWorkday.value)) {
      calendar.value.specialWorkDays.push(newSpecialWorkday.value)
      calendar.value.specialWorkDays.sort()
      newSpecialWorkday.value = ''
      markDirty()
    }
  }
}

const deleteSpecialWorkday = (date) => {
  calendar.value.specialWorkDays = (calendar.value.specialWorkDays || []).filter(d => d !== date)
  markDirty()
}

const addTimeInterval = () => {
  if (!calendar.value.workTimeIntervals) {
    calendar.value.workTimeIntervals = []
  }
  calendar.value.workTimeIntervals.push({ start: '09:00', end: '18:00' })
  markDirty()
}

const removeTimeInterval = (idx) => {
  calendar.value.workTimeIntervals.splice(idx, 1)
  markDirty()
}

// Cost Calculation
const calculateTaskCost = (task) => {
  if (task.isParent || task.duration === 0) return 0
  let cost = 0
  const dailyHours = calendar.value.dailyWorkHours || 8
  const units = task.resourceUnits || {}
  task.resourceIds.forEach(resId => {
    const res = resources.value.find(r => r.id === resId)
    if (res) {
      const pct = units[resId] !== undefined ? units[resId] : 100
      cost += task.duration * dailyHours * (pct / 100) * res.rate
    }
  })
  return cost
}

// Gantt dimensions
const zoomScale = computed(() => zoomLevel.value === 'day' ? 24 : 8)
const ganttWidth = computed(() => {
  return 200 + timelineCols.value.length * (zoomLevel.value === 'day' ? 60 : 80)
})
const ganttHeight = computed(() => 40 + wbs.value.length * 40 + 40)

const minStartDate = computed(() => {
  const dates = wbs.value.map(t => t.startDate).filter(Boolean)
  if (!dates.length) return new Date()
  const min = dates.reduce((minD, d) => d < minD ? d : minD, dates[0])
  const date = parseUTCDate(min)
  if (date) {
    date.setUTCDate(date.getUTCDate() - 2) // padding
    return date
  }
  return new Date()
})

const timelineCols = computed(() => {
  const cols = []
  const start = new Date(minStartDate.value.getTime())
  const isDay = zoomLevel.value === 'day'
  
  for (let i = 0; i < (isDay ? 24 : 12); i++) {
    const date = new Date(start.getTime())
    if (isDay) {
      date.setUTCDate(start.getUTCDate() + i)
      cols.push({
        x: 200 + i * 60,
        label: date.toISOString().slice(5, 10),
        dateStr: date.toISOString().slice(0, 10)
      })
    } else {
      date.setUTCDate(start.getUTCDate() + i * 7)
      cols.push({
        x: 200 + i * 80,
        label: `W${i + 1} (${date.toISOString().slice(5, 10)})`,
        dateStr: date.toISOString().slice(0, 10)
      })
    }
  }
  return cols
})

const getTaskBarX = (startDateStr) => {
  if (!startDateStr) return 0
  const start = new Date(minStartDate.value.getTime())
  const date = parseUTCDate(startDateStr)
  if (!date) return 0
  const diffTime = Math.abs(date - start)
  const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24))
  return 200 + diffDays * (zoomLevel.value === 'day' ? 60 : 8.5)
}

const getTaskBarWidth = (startDateStr, endDateStr) => {
  if (!startDateStr || !endDateStr) return 0
  const start = parseUTCDate(startDateStr)
  const end = parseUTCDate(endDateStr)
  if (!start || !end) return 0
  const diffTime = Math.abs(end - start)
  const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24)) + 1
  return diffDays * (zoomLevel.value === 'day' ? 60 : 8.5)
}

const getMilestonePoints = (rowIndex, dateStr) => {
  const x = getTaskBarX(dateStr)
  const y = 40 + rowIndex * 40 + 15
  return `${x},${y} ${x+8},${y+8} ${x},${y+16} ${x-8},${y+8}`
}

// Predecessor path lines (Gantt links)
const dependencyArrows = computed(() => {
  const arrows = []
  const taskMap = new Map(wbs.value.map(t => [t.wbsCode, t]))
  
  wbs.value.forEach((task, index) => {
    if (task.predecessors) {
      const predCodes = task.predecessors.split(',').map(s => s.trim()).filter(Boolean)
      predCodes.forEach(code => {
        const predTask = taskMap.get(code) || Array.from(taskMap.values()).find(t => String(t.id) === String(code) || String(t.wbsCode) === String(code))
        if (predTask && predTask.startDate && task.startDate) {
          const predIndex = wbs.value.indexOf(predTask)
          
          const x1 = getTaskBarX(predTask.startDate) + getTaskBarWidth(predTask.startDate, predTask.endDate)
          const y1 = 40 + predIndex * 40 + 15
          
          const x2 = getTaskBarX(task.startDate)
          const y2 = 40 + index * 40 + 15
          
          const path = `M ${x1} ${y1} L ${x1 + 12} ${y1} L ${x1 + 12} ${y2} L ${x2} ${y2}`
          arrows.push({ path })
        }
      })
    }
  })
  return arrows
})

// Baseline actions
const saveBaseline = () => {
  if (!baselineName.value) {
    ElMessage.warning('请输入基线名称')
    return
  }
  const id = Date.now()
  baselines.value.push({
    id,
    name: baselineName.value,
    createdAt: new Date().toISOString(),
    tasks: wbs.value.map(t => ({
      id: t.id,
      startDate: t.startDate,
      endDate: t.endDate,
      duration: t.duration,
      cost: calculateTaskCost(t)
    }))
  })
  selectedBaseline.value = id
  baselineName.value = ''
  showBaselineDialog.value = false
  saveToLocalStorage()
  ElMessage.success('成功保存当前基线并设为比对参考线')
}

const getBaselineTask = (taskId) => {
  if (!selectedBaseline.value) return null
  const base = baselines.value.find(b => b.id === selectedBaseline.value)
  if (!base) return null
  return base.tasks.find(t => t.id === taskId) || null
}

const getBaselineTaskBar = (taskId) => {
  const bTask = getBaselineTask(taskId)
  if (!bTask || !bTask.startDate || !bTask.endDate) return null
  return {
    x: getTaskBarX(bTask.startDate),
    width: getTaskBarWidth(bTask.startDate, bTask.endDate)
  }
}

const getBaselineTaskCost = (taskId) => {
  const bTask = getBaselineTask(taskId)
  return bTask ? bTask.cost : 0
}

const getScheduleVariance = (task) => {
  const bTask = getBaselineTask(task.id)
  if (!bTask || !bTask.duration || !task.duration) return 0
  return task.duration - bTask.duration
}

const calculateCostVariance = (task) => {
  const bCost = getBaselineTaskCost(task.id)
  const currentCost = calculateTaskCost(task)
  return currentCost - bCost
}

// Calculate resource workload for a specific column/date
const calculateResourceWorkload = (resId, col) => {
  let hoursAllocated = 0
  wbs.value.forEach(task => {
    if (!task.isParent && task.resourceIds.includes(resId) && task.startDate && task.endDate) {
      const tStart = parseUTCDate(task.startDate)
      const tEnd = parseUTCDate(task.endDate)
      const colDate = parseUTCDate(col.dateStr)
      if (!tStart || !tEnd || !colDate) return
      
      const units = task.resourceUnits || {}
      const pct = units[resId] !== undefined ? units[resId] : 100
      const dailyHours = (calendar.value.dailyWorkHours || 8) * (pct / 100)
      
      if (zoomLevel.value === 'week') {
        const wStart = new Date(colDate.getTime())
        const wEnd = new Date(colDate.getTime())
        wEnd.setUTCDate(wEnd.getUTCDate() + 7)
        let overlapWorkDays = 0
        let temp = new Date(wStart.getTime())
        while (temp < wEnd) {
          const tempStr = temp.toISOString().slice(0, 10)
          if (temp >= tStart && temp <= tEnd && isWorkingDay(tempStr)) {
            overlapWorkDays++
          }
          temp.setUTCDate(temp.getUTCDate() + 1)
        }
        hoursAllocated += overlapWorkDays * dailyHours
      } else {
        if (colDate >= tStart && colDate <= tEnd && isWorkingDay(col.dateStr)) {
          hoursAllocated += dailyHours
        }
      }
    }
  })
  return hoursAllocated
}

const getMatrixCellStyle = (hours) => {
  const limit = calendar.value.dailyWorkHours || 8
  if (hours > limit) {
    return {
      background: '#fee2e2',
      color: '#991b1b',
      fontWeight: 'bold',
      borderRight: '1px solid #e2e8f0'
    }
  }
  if (hours > 0) {
    return {
      background: '#ecfdf5',
      color: '#047857',
      borderRight: '1px solid #e2e8f0'
    }
  }
  return {
    color: '#94a3b8',
    borderRight: '1px solid #e2e8f0'
  }
}

// Resource Workload chart
let workloadChartInstance = null
const workloadChartRef = ref(null)

const renderWorkloadChart = async () => {
  await nextTick()
  if (!workloadChartRef.value) return

  if (workloadChartInstance) {
    workloadChartInstance.dispose()
  }
  workloadChartInstance = echarts.init(workloadChartRef.value)

  const days = timelineCols.value.map(c => c.label)
  
  const res = resources.value.find(r => r.id === selectedWorkloadResourceId.value)
  if (!res) {
    workloadChartInstance.setOption({
      title: { text: '请在资源管理库引入成员，并选择资源查看其每日额定负载趋势。', left: 'center', top: 'center' }
    })
    return
  }

  const dailyLimit = calendar.value.dailyWorkHours || 8
  const data = timelineCols.value.map(col => calculateResourceWorkload(res.id, col))

  workloadChartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 40, right: 20, top: 40, bottom: 40, containLabel: true },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', name: '分配负载(小时)' },
    series: [{
      name: res.name,
      type: 'bar',
      data,
      itemStyle: {
        color: (params) => {
          return params.value > dailyLimit ? '#f43f5e' : '#10b981'
        }
      },
      markLine: {
        data: [{ yAxis: dailyLimit, name: '额定工时上限' }],
        lineStyle: { color: '#ef4444', type: 'dashed' }
      }
    }]
  })
}

// AI Diagnostic analysis using mock GLM response
const runAiDiagnosis = () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++aiDiagnosisRequestId
  aiLoading.value = true
  
  const delayedTasks = wbs.value.filter(t => !t.isParent && getScheduleVariance(t) > 0)
  const milestoneCount = wbs.value.filter(t => t.duration === 0).length
  const totalCost = wbs.value.reduce((sum, t) => sum + calculateTaskCost(t), 0)
  
  setTimeout(() => {
    if (requestId !== aiDiagnosisRequestId || projectId.value !== currentProjectId) return
    aiLoading.value = false
    aiResult.value = `### 🤖 GLM-4.7 计划排程评估分析

1. **项目关键路径分析 (Critical Path Method)**
   - 关键路径为：**1.1 需求分析编制** ➔ **1.2 原型界面设计** ➔ **2.1 后端架构搭建与设计** ➔ **2.2 前端页面与图表开发**。
   - 最后一项交付物“项目上线与交付”无缝连接关键路径，整体排程结构合理。

2. **进度偏离与风险预警 (Variance & Risk)**
   ${delayedTasks.length > 0 
     ? `- 发现 **${delayedTasks.length}** 个任务超出计划基线（偏离共计 ${delayedTasks.reduce((s,t)=>s+getScheduleVariance(t),0)} 天）。主要延误点在：${delayedTasks.map(t=>t.title).join(', ')}。`
     : '- 所有活动均按基准线正常推进，未发现进度严重偏离。'}
   - **资源超载分析**：成员负载图与负载矩阵显示，部分日期存在资源分配超载现象（红色标示）。例如，前端开发在联调期间同时被分配了多个子任务，日工作负荷超 8 小时。建议重新配置部分前置关系的开始日期。

3. **里程碑管理 (Milestones)**
   - 本项目定义了 **${milestoneCount}** 个交付里程碑。下一里程碑为 **“项目上线与交付”**，预定完成时间为 **2026-06-29**。

4. **工时与成本控制指标 (EVM)**
   - 当前项目总预计预算成本：**$${totalCost}**。
   - 建议：安排周会重新平衡超负荷资源的计划，并通过“自动排期”重新平滑工期。`
  }, 1200)
}

// Export custom HTML deliverables
const exportToHtml = () => {
  const totalCost = wbs.value.reduce((sum, t) => sum + calculateTaskCost(t), 0)
  const milestones = wbs.value.filter(t => t.duration === 0).map(m => m.title).join(', ')

  let rowsHtml = wbs.value.map(t => {
    return `<tr>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.wbsCode}</td>
      <td style="padding: 8px; border: 1px solid #ddd; font-weight: ${t.isParent ? 'bold' : 'normal'}">${t.outlineCode ? t.outlineCode + ' ' : ''}${t.title}</td>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.duration}</td>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.startDate}</td>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.endDate}</td>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.predecessors || '-'}</td>
      <td style="padding: 8px; border: 1px solid #ddd;">${getResourceAssignmentText(t)}</td>
      <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${t.progress}%</td>
    </tr>`
  }).join('')

  const html = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>TeamFlowAI 项目计划管理报告</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 40px; color: #333; line-height: 1.6; }
    h1 { border-bottom: 2px solid #0284c7; padding-bottom: 12px; color: #1e293b; }
    .metric-card { display: inline-block; width: 200px; padding: 15px; margin-right: 15px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; text-align: center; }
    .metric-value { font-size: 24px; font-weight: bold; color: #0284c7; margin-top: 5px; }
    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
    th { background: #f1f5f9; padding: 10px; border: 1px solid #ddd; font-weight: bold; text-align: left; }
  </style>
</head>
<body>
  <h1>TeamFlowAI 项目计划管理报告</h1>
  <p>导出的计划版本，截止时间：${new Date().toLocaleString()}</p>
  
  <div style="margin: 20px 0;">
    <div class="metric-card">
      <div>计划总预算成本</div>
      <div class="metric-value">$${totalCost}</div>
    </div>
    <div class="metric-card">
      <div>里程碑事件</div>
      <div style="font-size:12px; font-weight:bold; color:#e11d48; margin-top:10px;">${milestones || '无'}</div>
    </div>
  </div>

  <h2>WBS 工作分解表格</h2>
  <table>
    <thead>
      <tr>
        <th>WBS</th>
        <th>任务名称</th>
        <th>工期(天)</th>
        <th>开始日期</th>
        <th>完成日期</th>
        <th>前置</th>
        <th>资源指派</th>
        <th>进度</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml}
    </tbody>
  </table>
</body>
</html>`

  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `TF-ProjectPlan-Export.html`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  ElMessage.success('成果包 HTML 报告已下载')
}

// Recalculate summary tasks reactively based on descendant updates using leaf descendants only
let isRecalculating = false
const recalculateWbsHierarchy = () => {
  if (isRecalculating) return
  isRecalculating = true
  try {
    let changed = true
    let iterations = 0
    while (changed && iterations < 5) {
      changed = false
      iterations++
      
      for (let i = wbs.value.length - 1; i >= 0; i--) {
        const task = wbs.value[i]
        if (task.isParent) {
          if (task.milestone) {
            task.milestone = false
          }
          const descendants = wbs.value.filter(c => !c.isParent && c.outlineCode && task.outlineCode && c.outlineCode.startsWith(task.outlineCode + '.'))
          if (descendants.length) {
            // 1. Dates
            const startDates = descendants.map(c => c.startDate).filter(Boolean)
            const endDates = descendants.map(c => c.endDate).filter(Boolean)
            if (startDates.length && endDates.length) {
              const minStart = startDates.reduce((min, d) => d < min ? d : min, startDates[0])
              const maxEnd = endDates.reduce((max, d) => d > max ? d : max, endDates[0])
              if (task.startDate !== minStart) {
                task.startDate = minStart
                changed = true
              }
              if (task.endDate !== maxEnd) {
                task.endDate = maxEnd
                changed = true
              }
            }

            // 2. Duration (working days count)
            if (task.startDate && task.endDate) {
              const workDaysCount = getWorkingDaysCount(task.startDate, task.endDate)
              if (task.duration !== workDaysCount) {
                task.duration = workDaysCount
                changed = true
              }
            }

            // 3. Progress (weighted by duration of leaf descendants)
            let totalDuration = 0
            let weightedProgress = 0
            descendants.forEach(c => {
              const dur = Number(c.duration) || 0
              const prog = Number(c.progress) || 0
              totalDuration += dur
              weightedProgress += prog * dur
            })
            const newProgress = totalDuration > 0 ? Math.round(weightedProgress / totalDuration) : 0
            if (task.progress !== newProgress) {
              task.progress = newProgress
              changed = true
            }
          }
        }
      }
    }
  } finally {
    isRecalculating = false
  }
}

// Plan Review & Discussion methods
const loadDeliverySection = async () => {
  const currentProjectId = projectId.value
  const requestId = ++deliverySectionRequestId
  resetDeliveryState()
  if (!currentProjectId) return

  try {
    const list = await projectSections(currentProjectId)
    if (requestId !== deliverySectionRequestId || projectId.value !== currentProjectId) return
    const found = list.find(s => s.sectionCode === 'MANAGEMENT_DELIVERY')
    if (found) {
      deliverySection.value = found
      const [commentsRes, reviewsRes, perms] = await Promise.all([
        sectionComments(found.id),
        sectionReviews(found.id),
        mySectionPermissions(found.id)
      ])
      if (requestId !== deliverySectionRequestId || projectId.value !== currentProjectId) return
      deliveryComments.value = commentsRes || []
      deliveryReviews.value = reviewsRes || []
      canReview.value = perms.includes('REVIEW')
    }
  } catch (err) {
    console.error('Failed to load project management section metadata', err)
  }
}

const submitDeliverySection = async () => {
  const currentProjectId = projectId.value
  const sectionId = deliverySection.value?.id
  if (!currentProjectId || !sectionId) return
  try {
    await saveToLocalStorage()
    if (projectId.value !== currentProjectId) return
    const markdownContent = buildWbsMarkdown()
    await saveSectionContent(sectionId, {
      title: '项目计划与 WBS 甘特图说明书',
      body: markdownContent
    })
    if (projectId.value !== currentProjectId) return
    await submitSection(sectionId)
    if (projectId.value !== currentProjectId) return
    ElMessage.success('项目管理计划已成功提交供全员审核！')
    await loadDeliverySection()
  } catch (err) {
    console.error(err)
    if (projectId.value === currentProjectId) {
      ElMessage.error('提交计划审核失败')
    }
  }
}

const handleDeliveryReview = async (result) => {
  const currentProjectId = projectId.value
  const sectionId = deliverySection.value?.id
  if (!currentProjectId || !sectionId) return
  try {
    await reviewSection(sectionId, {
      reviewResult: result,
      reviewComment: reviewForm.value.comment
    })
    if (projectId.value !== currentProjectId) return
    ElMessage.success(result === 'APPROVED' ? '已同意该项目计划' : '已打回该项目计划进行修改')
    reviewForm.value.comment = ''
    await loadDeliverySection()
  } catch (err) {
    console.error(err)
    if (projectId.value === currentProjectId) {
      ElMessage.error('审核处理提交失败')
    }
  }
}

const submitDeliveryComment = async () => {
  const currentProjectId = projectId.value
  const sectionId = deliverySection.value?.id
  if (!currentProjectId || !sectionId || !activeCommentText.value) return
  try {
    const detail = await sectionDetail(sectionId)
    if (projectId.value !== currentProjectId) return
    if (!detail.latestContent) {
      ElMessage.warning('计划暂未生成草稿或提交，无法发表评论')
      return
    }
    await commentSection(sectionId, {
      contentId: detail.latestContent.id,
      commentText: activeCommentText.value
    })
    if (projectId.value !== currentProjectId) return
    ElMessage.success('发表留言成功')
    activeCommentText.value = ''
    const comments = await sectionComments(sectionId)
    if (projectId.value !== currentProjectId) return
    deliveryComments.value = comments
  } catch (err) {
    console.error(err)
    if (projectId.value === currentProjectId) {
      ElMessage.error('发表留言失败')
    }
  }
}

const importWbsToDocument = async () => {
  const currentProjectId = projectId.value
  const sectionId = deliverySection.value?.id
  if (!currentProjectId || !sectionId) return
  try {
    const markdownContent = buildWbsMarkdown()
    await saveSectionContent(sectionId, {
      title: '项目计划与 WBS 甘特图说明书 (全员审批通过导入)',
      body: markdownContent
    })
    if (projectId.value !== currentProjectId) return
    ElMessage.success('项目 WBS、甘特图和 AI 计划助理诊断报告已成功一键导入项目文档中！')
    await loadDeliverySection()
  } catch (err) {
    console.error(err)
    if (projectId.value === currentProjectId) {
      ElMessage.error('导入文档失败')
    }
  }
}

const buildWbsMarkdown = () => {
  let markdown = ''
  
  markdown += `## 1. 项目整体计划与基线说明\n\n`
  markdown += `当前项目总预计预算成本：**$${wbs.value.reduce((sum, t) => sum + calculateTaskCost(t), 0)}**。\n`
  markdown += `常规工作日设定：每周工作 ${calendar.value.workDays.length} 天，日标准工时 ${calendar.value.dailyWorkHours} 小时。\n\n`
  
  if (aiResult.value) {
    markdown += `## 2. 🤖 GLM 计划排程评估与 AI 诊断报告\n\n`
    const cleanAiResult = aiResult.value.replace(/### 🤖 GLM-4.7 计划排程评估分析\n?/, '')
    markdown += cleanAiResult + `\n\n`
  }
  
  markdown += `## 3. WBS 任务分解表\n\n`
  markdown += `| WBS编码 | 任务名称 | 工期(天) | 开始日期 | 结束日期 | 前置任务 | 分配资源 | 进度 |\n`
  markdown += `| :--- | :--- | :---: | :---: | :---: | :---: | :--- | :---: |\n`
  
  wbs.value.forEach(t => {
    const indent = '&nbsp;'.repeat(t.level * 4)
    const titleText = t.isParent ? `**${t.title}**` : t.title
    markdown += `| ${t.outlineCode || ''} | ${indent}${titleText} | ${t.duration} | ${t.startDate || '-'} | ${t.endDate || '-'} | ${t.predecessors || '-'} | ${getResourceAssignmentText(t)} | ${t.progress}% |\n`
  })
  
  markdown += `\n`
  
  markdown += `## 4. 项目甘特图 (PlantUML)\n\n`
  markdown += `\`\`\`plantuml\n`
  markdown += `@startgantt\n`
  markdown += `title 项目进度甘特图\n`
  markdown += `language zh\n\n`
  
  wbs.value.forEach(t => {
    if (t.startDate && t.endDate) {
      const cleanTitle = t.title.replace(/[\[\]]/g, '')
      if (t.duration === 0) {
        markdown += `[${cleanTitle}] happens ${t.startDate}\n`
      } else {
        markdown += `[${cleanTitle}] starts ${t.startDate} and ends ${t.endDate}\n`
      }
    }
  })
  
  wbs.value.forEach(t => {
    if (t.predecessors) {
      const preds = t.predecessors.split(',').map(s => s.trim()).filter(Boolean)
      preds.forEach(p => {
        const predTask = wbs.value.find(item => item.wbsCode === p || item.id === p)
        if (predTask) {
          const cleanTitle = t.title.replace(/[\[\]]/g, '')
          const cleanPredTitle = predTask.title.replace(/[\[\]]/g, '')
          markdown += `[${cleanTitle}] starts at [${cleanPredTitle}]'s end\n`
        }
      })
    }
  })
  
  markdown += `@endgantt\n`
  markdown += `\`\`\`\n`
  
  return markdown
}

// Watchers
watch(wbs, () => {
  recalculateWbsHierarchy()
}, { deep: true })

watch(activeTab, (newTab) => {
  if (newTab === 'workload') {
    nextTick(() => {
      renderWorkloadChart()
    })
  } else if (newTab === 'planReview') {
    loadDeliverySection()
  }
})

watch(projectId, () => {
  checkMembership()
  loadData()
  loadDeliverySection()
}, { immediate: true })

// Resize charts on window resize
const handleResize = () => {
  workloadChartInstance?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  workloadChartInstance?.dispose()
})
</script>

<style scoped>
h3 {
  margin-top: 0;
}
table th, table td {
  border: 1px solid #e2e8f0;
  padding: 8px;
}
</style>
