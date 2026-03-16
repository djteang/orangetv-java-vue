import CryptoJS from 'crypto-js'

/**
 * 机器码生成和验证工具
 * 基于设备指纹技术生成唯一的机器码用于用户绑定
 */
export class MachineCode {
  static async generateFingerprint(): Promise<{
    userAgent: string
    language: string
    timezone: string
    screen: string
    colorDepth: number
    platform: string
    canvasFingerprint: string
    webglFingerprint: string
    cookieEnabled: boolean
    doNotTrack: string
    hardwareConcurrency: number
    maxTouchPoints: number
  }> {
    const userAgent = navigator.userAgent
    const language = navigator.language
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    const screen = `${window.screen.width}x${window.screen.height}x${window.screen.colorDepth}`
    const colorDepth = window.screen.colorDepth
    const platform = navigator.platform
    const cookieEnabled = navigator.cookieEnabled
    const doNotTrack = navigator.doNotTrack || 'unknown'
    const hardwareConcurrency = navigator.hardwareConcurrency || 0
    const maxTouchPoints = navigator.maxTouchPoints || 0

    const canvasFingerprint = this.getCanvasFingerprint()
    const webglFingerprint = this.getWebGLFingerprint()

    return {
      userAgent,
      language,
      timezone,
      screen,
      colorDepth,
      platform,
      canvasFingerprint,
      webglFingerprint,
      cookieEnabled,
      doNotTrack,
      hardwareConcurrency,
      maxTouchPoints,
    }
  }

  private static getCanvasFingerprint(): string {
    try {
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')
      if (!ctx) return 'no-canvas'

      canvas.width = 280
      canvas.height = 60

      ctx.textBaseline = 'top'
      ctx.font = '14px Arial'
      ctx.fillStyle = '#f60'
      ctx.fillRect(125, 1, 62, 20)

      ctx.fillStyle = '#069'
      ctx.fillText('Device Fingerprint 🔒', 2, 15)

      ctx.fillStyle = 'rgba(102, 204, 0, 0.7)'
      ctx.fillText('Machine Code Generator', 4, 35)

      ctx.globalCompositeOperation = 'multiply'
      ctx.fillStyle = 'rgb(255,0,255)'
      ctx.beginPath()
      ctx.arc(50, 50, 50, 0, Math.PI * 2, true)
      ctx.closePath()
      ctx.fill()

      return canvas.toDataURL()
    } catch {
      return 'canvas-error'
    }
  }

  private static getWebGLFingerprint(): string {
    try {
      const canvas = document.createElement('canvas')
      const gl = (canvas.getContext('webgl') ||
        canvas.getContext('experimental-webgl')) as WebGLRenderingContext
      if (!gl) return 'no-webgl'

      const debugInfo = gl.getExtension('WEBGL_debug_renderer_info')
      const vendor = debugInfo
        ? gl.getParameter((debugInfo as any).UNMASKED_VENDOR_WEBGL)
        : ''
      const renderer = debugInfo
        ? gl.getParameter((debugInfo as any).UNMASKED_RENDERER_WEBGL)
        : ''

      const webglInfo = {
        vendor,
        renderer,
        version: gl.getParameter(gl.VERSION),
        shadingLanguageVersion: gl.getParameter(gl.SHADING_LANGUAGE_VERSION),
        extensions: gl.getSupportedExtensions()?.join(',') || '',
      }

      return JSON.stringify(webglInfo)
    } catch {
      return 'webgl-error'
    }
  }

  static async generateMachineCode(): Promise<string> {
    try {
      const fingerprint = await this.generateFingerprint()
      const fingerprintString = JSON.stringify(fingerprint)
      const hash = CryptoJS.SHA256(fingerprintString)
      return hash.toString(CryptoJS.enc.Hex).substring(0, 32).toUpperCase()
    } catch (error) {
      console.error('生成机器码失败:', error)
      const fallback = `${Date.now()}-${Math.random().toString(36).substring(2)}`
      return CryptoJS.SHA256(fallback)
        .toString(CryptoJS.enc.Hex)
        .substring(0, 32)
        .toUpperCase()
    }
  }

  static formatMachineCode(machineCode: string): string {
    if (!machineCode || machineCode.length !== 32) return machineCode
    return machineCode.match(/.{1,4}/g)?.join('-') || machineCode
  }

  static async getDeviceInfo(): Promise<string> {
    try {
      const fingerprint = await this.generateFingerprint()
      const browser = this.getBrowserInfo(fingerprint.userAgent)
      const os = this.getOSInfo(fingerprint.userAgent)
      return `${browser} / ${os} / ${fingerprint.screen}`
    } catch {
      return '未知设备'
    }
  }

  private static getBrowserInfo(userAgent: string): string {
    if (userAgent.includes('Chrome') && !userAgent.includes('Edge')) {
      const match = userAgent.match(/Chrome\/([0-9.]+)/)
      return `Chrome ${match ? match[1] : ''}`
    }
    if (userAgent.includes('Firefox')) {
      const match = userAgent.match(/Firefox\/([0-9.]+)/)
      return `Firefox ${match ? match[1] : ''}`
    }
    if (userAgent.includes('Safari') && !userAgent.includes('Chrome')) {
      const match = userAgent.match(/Version\/([0-9.]+)/)
      return `Safari ${match ? match[1] : ''}`
    }
    if (userAgent.includes('Edge')) {
      const match = userAgent.match(/Edge\/([0-9.]+)/)
      return `Edge ${match ? match[1] : ''}`
    }
    return 'Unknown Browser'
  }

  private static getOSInfo(userAgent: string): string {
    if (userAgent.includes('Windows NT 10.0')) return 'Windows 10/11'
    if (userAgent.includes('Windows NT 6.3')) return 'Windows 8.1'
    if (userAgent.includes('Windows NT 6.2')) return 'Windows 8'
    if (userAgent.includes('Windows NT 6.1')) return 'Windows 7'
    if (userAgent.includes('Windows')) return 'Windows'

    if (userAgent.includes('Mac OS X')) {
      const match = userAgent.match(/Mac OS X ([0-9_]+)/)
      return `macOS ${match ? match[1].replace(/_/g, '.') : ''}`
    }

    if (userAgent.includes('Linux')) return 'Linux'
    if (userAgent.includes('Android')) {
      const match = userAgent.match(/Android ([0-9.]+)/)
      return `Android ${match ? match[1] : ''}`
    }
    if (userAgent.includes('iPhone') || userAgent.includes('iPad')) {
      const match = userAgent.match(/OS ([0-9_]+)/)
      return `iOS ${match ? match[1].replace(/_/g, '.') : ''}`
    }

    return 'Unknown OS'
  }

  static isSupported(): boolean {
    return !!(
      typeof window !== 'undefined' &&
      window.navigator &&
      window.screen &&
      document.createElement
    )
  }
}

export default MachineCode
