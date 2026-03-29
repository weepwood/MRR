import axios from 'axios'
import { clearSession, getToken } from '@/utils/session'

export const loginApi = axios.create({
  baseURL: '/loginApi',
  timeout: 30000
})

export const authApi = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export const searchApi = axios.create({
  baseURL: '/searchApi',
  timeout: 30000
})

authApi.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

authApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      clearSession()
      if (window.location.pathname !== '/login' && window.location.pathname !== '/') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)
