import axios from 'axios'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',  // Vite 代理会处理 http://localhost:8080/api
  timeout: 30000,
  withCredentials: true // ✅ 关键：确保跨域请求时携带 JSESSIONID Cookie
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 可以在这里添加 token 等
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 后端返回格式：{ code: 200, message: "success", data: {...} }
    const { code, message, data } = response.data
    
    // 如果返回的状态码不是 200，则认为是错误
    if (code !== 200) {
      return Promise.reject(new Error(message || 'Error'))
    }
    
    // 返回 data 部分，简化使用逻辑 (适配前端开发文档 3.2)
    return data
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

export default request
