<template>
  <img v-if="srcUrl" :src="srcUrl" :alt="alt" loading="lazy" />
  <div v-else-if="loading" class="image-loading" style="padding: 16px; text-align: center; color: #98a2b3; font-size: 13px; border: 1px dashed #e5e7eb; border-radius: 6px;">图片加载中...</div>
  <div v-else class="image-error" style="padding: 16px; text-align: center; color: #f43f5e; font-size: 13px; border: 1px dashed #fca5a5; border-radius: 6px; background: #fff5f5;">图片加载失败</div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import request from '../../utils/request'

const props = defineProps({
  src: {
    type: String,
    required: true
  },
  alt: {
    type: String,
    default: ''
  }
})

const srcUrl = ref('')
const loading = ref(false)
let currentObjectUrl = ''

const revokeCurrentUrl = () => {
  if (currentObjectUrl) {
    URL.revokeObjectURL(currentObjectUrl)
    currentObjectUrl = ''
  }
}

const loadImage = async () => {
  revokeCurrentUrl()
  srcUrl.value = ''

  if (!props.src) return

  // If it's a local backend file, fetch it using axios to attach the Authorization token
  if (props.src.startsWith('/api/files/') || props.src.includes('/api/files/')) {
    loading.value = true
    try {
      // request baseURL is '/api', so strip '/api' prefix from request path
      const relativeUrl = props.src.replace(/^\/api/, '')
      const blob = await request.get(relativeUrl, { responseType: 'blob' })
      
      const objectUrl = URL.createObjectURL(blob)
      currentObjectUrl = objectUrl
      srcUrl.value = objectUrl
    } catch (error) {
      console.error('Failed to load local authenticated image:', error)
    } finally {
      loading.value = false
    }
  } else {
    // Direct load for external images
    srcUrl.value = props.src
  }
}

watch(() => props.src, loadImage, { immediate: true })

onBeforeUnmount(() => {
  revokeCurrentUrl()
})
</script>
