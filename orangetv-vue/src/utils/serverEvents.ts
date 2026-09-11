export interface ServerEvent { event: string; data: string }

// 支持网络分片、多行 data、注释及 CRLF；不能按单次 fetch chunk 直接解析 JSON。
export function createServerEventParser(onEvent: (event: ServerEvent) => void) {
  let buffer = ''
  let event = 'message'
  let data: string[] = []
  function dispatch() {
    if (data.length) onEvent({ event, data: data.join('\n') })
    event = 'message'
    data = []
  }
  function line(value: string) {
    if (!value) { dispatch(); return }
    if (value.startsWith(':')) return
    const colon = value.indexOf(':')
    const field = colon < 0 ? value : value.slice(0, colon)
    let content = colon < 0 ? '' : value.slice(colon + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (field === 'event') event = content
    if (field === 'data') data.push(content)
  }
  return {
    push(chunk: string) {
      buffer += chunk
      let end: number
      while ((end = buffer.search(/[\r\n]/)) >= 0) {
        if (buffer[end] === '\r' && end === buffer.length - 1) break
        const length = buffer[end] === '\r' && buffer[end + 1] === '\n' ? 2 : 1
        line(buffer.slice(0, end))
        buffer = buffer.slice(end + length)
      }
    },
    finish() {
      if (buffer) line(buffer.replace(/\r$/, ''))
      buffer = ''
      dispatch()
    },
  }
}
