<template>
  <div class="panel stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  hint: String
})
</script>

<style scoped>
.stat-card {
  min-height: 104px;
}
.stat-label {
  color: #667085;
  font-size: 13px;
}
.stat-value {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 800;
  color: #0f766e;
}
.stat-hint {
  margin-top: 6px;
  color: #8a94a6;
  font-size: 12px;
}
</style>
