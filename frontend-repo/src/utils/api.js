import axios from 'axios'

const loginApi = axios.create({
    baseURL: '/loginApi',
    timeout: 30000
})

// 创建带认证的axios实例
const authApi = axios.create({
    baseURL: '/api',
    timeout: 30000
})

const searchApi = axios.create({
    baseURL: '/searchApi',
    timeout: 30000
})

// 请求拦截器：自动添加token到请求头
authApi.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器：处理token过期
authApi.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401 || error.response?.status === 403) {
            // token过期，清除本地存储并跳转到登录页
            localStorage.removeItem('token')
            window.location.reload()
        }
        return Promise.reject(error)
    }
)

/**
 * 登录接口
 * @param {object} credentials 登录凭据 {username, password}
 * @returns {Promise} 返回 axios Promise
 */
export function login(credentials) {
    // 通过专用实例，复用超时与后续可加的拦截器
    return loginApi.post('', credentials)
}

/**
 * 请求 /img-api/{bah} 接口
 * @param {string} bah 病案号
 * @param {object} [config] 可选的 axios 配置
 * @returns {Promise} 返回 axios Promise
 */
export function getImgApiByBah(bah, config = {}) {
    return authApi.get(`/img-api/${bah}`, config)
}

// 下载病案压缩包 /download/{BAH}
export function downloadBah(bah, config = {}) {
    return authApi.get(`/img-api/download/${bah}`, config)
}


// 获取病案图片
export function getImg(cx, config = {}){
    return authApi.get(`/img-api/image/${cx}`, config)
}

// 根据身份证号获取该身份证号下所有的病案信息
export function getBAHByIdCard(idCard){
    return searchApi.get(`/search/getBAHByID/${idCard}`)
}


// 根据加密身份证号获取该身份证号下所有的病案信息
export function getBAHByEncryptID(EncryptID, userId, iv, timestamp){
    return searchApi.get(`/search/getBAHByEncryptID`, {
        params: {
            EncryptID,
            userId,
            iv,
            timestamp
        }
    })
}

// 修改图片类型，图片类型通过 {"btype": "1"} 传入
export function updateImgType(imageId, btype){
    return authApi.put(`/img-api/updateImageType/${imageId}`, btype)
}

/**
 * 获取病案列表（分页）
 * @param {object} params 查询参数 {page, size, bah, brxh 等}
 * @returns {Promise} 返回 axios Promise
 */
export function getRecords(params = {}) {
    return authApi.get('/scan-api/page', { params })
}

/**
 * 根据病案号获取病案详情
 * @param {string} bah 病案号
 * @returns {Promise} 返回 axios Promise
 */
export function getRecordByBah(bah) {
    return authApi.get(`/scan-api/${bah}`)
}

/**
 * 更新病案信息
 * @param {number} id 病案 ID
 * @param {object} data 更新数据
 * @returns {Promise} 返回 axios Promise
 */
export function updateRecord(id, data) {
    return authApi.put(`/scan-api/${id}`, data)
}

/**
 * 删除病案
 * @param {number} id 病案 ID
 * @returns {Promise} 返回 axios Promise
 */
export function deleteRecord(id) {
    return authApi.delete(`/scan-api/${id}`)
}

/**
 * 批量下载病案
 * @param {array} ids 病案 ID 数组
 * @returns {Promise} 返回 axios Promise
 */
export function batchDownloadRecords(ids) {
    return authApi.post('/scan-api/batch-download', { ids }, { responseType: 'blob' })
}

/**
 * 获取系统运行时信息
 * @returns {Promise} 返回 axios Promise
 */
export function getSystemRuntime() {
    return authApi.get('/system/runtime')
}

/**
 * 获取系统属性信息
 * @returns {Promise} 返回 axios Promise
 */
export function getSystemProperties() {
    return authApi.get('/system/properties')
}

/**
 * 获取系统内存信息
 * @returns {Promise} 返回 axios Promise
 */
export function getSystemMemory() {
    return authApi.get('/system/memory')
}

/**
 * 获取系统详细信息
 * @returns {Promise} 返回 axios Promise
 */
export function getSystemInfo() {
    return authApi.get('/system/info')
}

/**
 * 获取系统健康状态
 * @returns {Promise} 返回 axios Promise
 */
export function getSystemHealth() {
    return authApi.get('/system/health')
}

/**
 * 获取病案统计概览信息
 * @returns {Promise} 返回 axios Promise
 */
export function getStatisticsSummary() {
    return authApi.get('/statistics-api/summary')
}

/**
 * 获取病案按日期统计数据
 * @returns {Promise} 返回 axios Promise
 */
export function getStatisticsDateSummary() {
    return authApi.get('/statistics-api/date-summary')
}

/**
 * 获取病案仪表盘数据
 * @returns {Promise} 返回 axios Promise
 */
export function getDashboardData() {
    return authApi.get('/statistics-api/dashboard')
}

/**
 * 获取病案统计明细列表
 * @param {number} page - 页码，默认 1
 * @param {number} size - 每页数量，默认 100
 * @returns {Promise} 返回 axios Promise
 */
export function getStatisticsList(page = 1, size = 100) {
    return authApi.get('/statistics-api', {
        params: {
            page,
            size
        }
    })
}
