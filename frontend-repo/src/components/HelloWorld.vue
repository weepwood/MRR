<script setup>
import { ref } from 'vue'
import { getImgApiByBah } from '@/utils/api'
import ImageGallery from './ImageGallery.vue'

defineProps({
  msg: String,
})

const count = ref(0)

const handleGetImg = () => {
  getImgApiByBah('00789508').then(res => {
    console.log(res)
  })
}
</script>

<template>

  <!-- <div class="hello">
    <h1>{{ msg }}</h1>
    <button @click="count++">You clicked me {{ count }} times.</button>
  </div>
  <button @click="handleGetImg">获取图片</button> -->
  <ImageGallery :images="images" />
</template>

<style scoped>
.read-the-docs {
  color: #888;
}
</style>
