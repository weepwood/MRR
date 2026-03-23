<template>
  <div class="test-page">
    <h2>接口测试 & 浏览器/IP 信息</h2>

    <div class="grid">
      <section class="card">
        <h3>接口测试</h3>
        <div class="form">
          <div class="row">
            <label>请求地址</label>
            <input v-model="requestUrl" placeholder="https://example.com/api" />
          </div>
          <div class="row">
            <label>方法</label>
            <select v-model="requestMethod">
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>PATCH</option>
              <option>DELETE</option>
            </select>
            <button class="send-btn" :disabled="sending" @click="sendRequest">{{ sending ? '发送中...' : '发送请求' }}</button>
          </div>
          <div class="row col">
            <label>Headers (JSON)</label>
            <textarea v-model="headersInput" rows="6" placeholder='{"Content-Type":"application/json"}' />
          </div>
          <div class="row col" v-if="requestMethod !== 'GET'">
            <label>Body (JSON)</label>
            <textarea v-model="bodyInput" rows="8" placeholder='{"foo":"bar"}' />
          </div>
        </div>

        <div class="result" v-if="response" >
          <div class="meta">
            <div>状态: <strong>{{ response.status }}</strong> {{ response.statusText }}</div>
            <div>耗时: <strong>{{ responseTimeMs }} ms</strong></div>
            <div>大小: <strong>{{ prettySize(responseSizeBytes) }}</strong></div>
          </div>
          <pre class="code">{{ formattedResponse }}</pre>
        </div>
        <div class="error" v-if="error">{{ error }}</div>
      </section>

      <section class="card">
        <h3>浏览器信息</h3>
        <ul class="info-list">
          <li><span>UA</span><code>{{ browserInfo.userAgent }}</code></li>
          <li><span>语言</span><code>{{ browserInfo.language }}</code></li>
          <li><span>平台</span><code>{{ browserInfo.platform }}</code></li>
          <li><span>时区</span><code>{{ browserInfo.timezone }}</code></li>
          <li><span>视口</span><code>{{ browserInfo.viewport }}</code></li>
        </ul>
      </section>

      <section class="card">
        <h3>功能测试</h3>
        <div class="form">
          <div class="row">
            <button class="send-btn" @click="testMultiSelect">测试多选功能</button>
            <button class="send-btn" @click="testPrintPage">测试打印页面</button>
          </div>
        </div>
      </section>

      <section class="card">
        <h3>IP 信息</h3>
        <ul class="info-list">
          <li>
            <span>本地IP</span>
            <code v-if="localIps.length">{{ localIps.join(', ') }}</code>
            <code v-else class="muted">无法直接获取（受浏览器隐私限制）</code>
          </li>
        </ul>
        <div class="muted" style="margin:8px 0 4px;">公网信息（参考）</div>
        <div v-if="ipLoading" class="muted">获取中...</div>
        <div v-else>
          <ul class="info-list" v-if="ipInfo">
            <li><span>公网IP</span><code>{{ ipInfo.ip || ipInfo.query }}</code></li>
            <li v-if="ipInfo.org || ipInfo.asn"><span>ISP/ASN</span><code>{{ ipInfo.org || ipInfo.asn }}</code></li>
            <li v-if="ipInfo.city || ipInfo.region"><span>位置</span><code>{{ ipInfo.city }} {{ ipInfo.region }}</code></li>
            <li v-if="ipInfo.country || ipInfo.country_name"><span>国家/地区</span><code>{{ ipInfo.country || ipInfo.country_name }}</code></li>
          </ul>
          <div v-else class="muted">未获取到 公网 IP 信息</div>
        </div>
      </section>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const requestUrl = ref('')
const requestMethod = ref('GET')
const headersInput = ref('')
const bodyInput = ref('')
const sending = ref(false)
const error = ref('')
const response = ref(null)
const responseBody = ref(null)
const responseTimeMs = ref(0)
const responseSizeBytes = ref(0)

const safeParseJson = (text, fallback) => {
  if (!text) return fallback
  try { return JSON.parse(text) } catch { return fallback }
}

const sendRequest = async () => {
  error.value = ''
  response.value = null
  responseBody.value = null
  responseTimeMs.value = 0
  responseSizeBytes.value = 0
  if (!requestUrl.value) { error.value = '请输入请求地址'; return }
  const headers = safeParseJson(headersInput.value, {})
  const bodyObj = safeParseJson(bodyInput.value, undefined)
  const init = { method: requestMethod.value, headers }
  if (requestMethod.value !== 'GET' && bodyObj !== undefined) {
    init.body = JSON.stringify(bodyObj)
    if (!init.headers) init.headers = {}
    if (!init.headers['Content-Type'] && !init.headers['content-type']) init.headers['Content-Type'] = 'application/json'
  }
  sending.value = true
  const start = performance.now()
  try {
    const res = await fetch(requestUrl.value, init)
    const end = performance.now()
    response.value = { status: res.status, statusText: res.statusText, ok: res.ok, headers: {} }
    res.headers.forEach((v, k) => { response.value.headers[k] = v })
    responseTimeMs.value = Math.round(end - start)
    const ct = res.headers.get('content-type') || ''
    if (ct.includes('application/json')) {
      const json = await res.json()
      const jsonStr = JSON.stringify(json)
      responseSizeBytes.value = jsonStr ? new Blob([jsonStr]).size : 0
      responseBody.value = json
    } else {
      const text = await res.text()
      responseSizeBytes.value = text ? new Blob([text]).size : 0
      responseBody.value = text
    }
  } catch (e) {
    error.value = e?.message || '请求失败'
  } finally {
    sending.value = false
  }
}

const formattedResponse = computed(() => {
  if (responseBody.value == null) return ''
  try { return typeof responseBody.value === 'string' ? responseBody.value : JSON.stringify(responseBody.value, null, 2) } catch { return String(responseBody.value) }
})

const prettySize = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B','KB','MB','GB']
  let i = 0; let n = bytes
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++ }
  return `${n.toFixed(2)} ${units[i]}`
}

const browserInfo = ref({ userAgent: '', language: '', platform: '', timezone: '', viewport: '' })
const ipInfo = ref(null)
const ipLoading = ref(false)
const localIps = ref([])

const loadBrowserInfo = () => {
  try {
    browserInfo.value.userAgent = navigator.userAgent
    browserInfo.value.language = navigator.language
    browserInfo.value.platform = navigator.platform
    browserInfo.value.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    browserInfo.value.viewport = `${window.innerWidth} x ${window.innerHeight}`
  } catch (e) {}
}

const loadIpInfo = async () => {
  ipLoading.value = true
  ipInfo.value = null
  try {
    // 优先使用 ipapi，其次回退到 ipify
    const res = await fetch('https://ipapi.co/json/')
    if (res.ok) { ipInfo.value = await res.json() }
    else {
      const r2 = await fetch('https://api.ipify.org?format=json')
      ipInfo.value = r2.ok ? await r2.json() : null
    }
  } catch (e) { ipInfo.value = null }
  finally { ipLoading.value = false }
}

const testMultiSelect = () => {
  // 跳转到图片画廊页面测试多选功能
  window.location.href = '/00788222'
}

const testPrintPage = () => {
  // 创建一些测试数据
  const testImages = [
    {
      id: 1,
      pages: 1,
      btype: 1,
      cx: 'https://via.placeholder.com/800x600/667eea/ffffff?text=Test+Image+1',
      blobUrl: 'https://via.placeholder.com/800x600/667eea/ffffff?text=Test+Image+1'
    },
    {
      id: 2,
      pages: 2,
      btype: 2,
      cx: 'https://via.placeholder.com/800x600/48bb78/ffffff?text=Test+Image+2',
      blobUrl: 'https://via.placeholder.com/800x600/48bb78/ffffff?text=Test+Image+2'
    },
    {
      id: 3,
      pages: 3,
      btype: 3,
      cx: 'https://via.placeholder.com/800x600/e74c3c/ffffff?text=Test+Image+3',
      blobUrl: 'https://via.placeholder.com/800x600/e74c3c/ffffff?text=Test+Image+3'
    }
  ]
  
  const testRecord = {
    bah: '00788222',
    name: '测试患者',
    department: '测试科室'
  }
  
  // 存储测试数据
  sessionStorage.setItem('selectedImagesForPrint', JSON.stringify(testImages))
  sessionStorage.setItem('printBah', '00788222')
  sessionStorage.setItem('printRecord', JSON.stringify(testRecord))
  
  // 跳转到打印页面
  window.open('/print', '_blank')
}

onMounted(() => {
  loadBrowserInfo()
  loadIpInfo()
  window.addEventListener('resize', loadBrowserInfo)
  // 获取本地网络 IP（可能受浏览器权限限制）
  try {
    const RTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection
    if (RTCPeerConnection) {
      const pc = new RTCPeerConnection({ iceServers: [] })
      pc.createDataChannel('')
      pc.onicecandidate = (e) => {
        if (!e || !e.candidate || !e.candidate.candidate) return
        const m = /([0-9]{1,3}(?:\.[0-9]{1,3}){3})/.exec(e.candidate.candidate)
        if (m && m[1] && !localIps.value.includes(m[1])) localIps.value.push(m[1])
      }
      pc.createOffer().then((sdp) => pc.setLocalDescription(sdp)).catch(() => {})
      setTimeout(() => { try { pc.close() } catch (e) {} }, 3000)
    }
  } catch (e) {}
})
</script>

<style scoped>
.test-page { padding: 20px; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.card { background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); padding: 16px; }
.card h3 { margin: 0 0 12px 0; }
.form { display: flex; flex-direction: column; gap: 10px; }
.row { display: flex; align-items: center; gap: 8px; }
.row.col { flex-direction: column; align-items: stretch; }
label { min-width: 72px; color: #333; }
input, select, textarea { flex: 1; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px; font-size: 14px; }
textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
.send-btn { padding: 8px 14px; background: #667eea; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.send-btn:disabled { opacity: .6; cursor: not-allowed; }
.result { margin-top: 12px; }
.meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 8px; color: #555; }
.code { background: #0b1020; color: #e2e8f0; border-radius: 8px; padding: 12px; max-height: 360px; overflow: auto; font-size: 13px; }
.info-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
.info-list li { display: grid; grid-template-columns: 120px 1fr; align-items: center; gap: 8px; }
.info-list span { color: #444; }
.info-list code { background: #f3f4f6; padding: 3px 6px; border-radius: 4px; }
.muted { color: #777; }

@media (max-width: 860px) { .grid { grid-template-columns: 1fr; } }
</style>