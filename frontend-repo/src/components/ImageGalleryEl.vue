<script setup>
import { computed } from 'vue'
import ImageGalleryElCore from './ImageGalleryEl-3.vue'

const props = defineProps({
  bah: { type: String, default: '' },
  idCard: { type: String, default: '' },
  encryptedIdCard: { type: String, default: '' },
  userid: { type: String, default: '' },
  userId: { type: String, default: '' },
  iv: { type: String, default: '' },
  timestamp: { type: String, default: '' }
})

const normalizedUserId = computed(() => props.userId || props.userid || '')
</script>

<template>
  <ImageGalleryElCore
    :bah="props.bah"
    :id-card="props.idCard"
    :encrypted-id-card="props.encryptedIdCard"
    :user-id="normalizedUserId"
    :iv="props.iv"
    :timestamp="props.timestamp"
  />
</template>
