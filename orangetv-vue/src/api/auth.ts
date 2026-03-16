import request from './index'

export interface LoginRequest {
  username: string
  password: string
  machineCode?: string
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface LoginResponse {
  ok: boolean
  token: string
  username: string
  role: string
  avatar?: string
  machineCodeBound: boolean
  expiresIn: number
}

// 登录
export function login(data: LoginRequest): Promise<LoginResponse> {
  return request.post('/login', data)
}

// 注册
export function register(data: RegisterRequest): Promise<LoginResponse> {
  return request.post('/register', data)
}

// 登出
export function logout(): Promise<void> {
  return request.post('/logout')
}

// 修改密码
export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return request.post('/change-password', data)
}

// 获取当前用户信息
export function getCurrentUser(): Promise<{ username: string; role: string; avatar?: string }> {
  return request.get('/me')
}
