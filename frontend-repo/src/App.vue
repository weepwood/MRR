<script setup>
</script>

<template>
  <div id="app">
    <router-view />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  line-height: 1.6;
  color: #333;
  background: #f5f7fb;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  height: 100%;
}

/* 平滑滚动与滚动条美化（仅支持的浏览器生效） */
html {
  scroll-behavior: smooth;
}

html, body {
  height: 100%;
}

/* Chromium/Edge 滚动条 */
*::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}
*::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.2);
  border-radius: 8px;
}
*::-webkit-scrollbar-thumb:hover {
  background: rgba(0,0,0,0.35);
}
*::-webkit-scrollbar-track {
  background: transparent;
}

/* Firefox */
* {
  scrollbar-width: thin;
  scrollbar-color: rgba(0,0,0,0.25) transparent;
}
</style>