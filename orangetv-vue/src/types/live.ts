export interface LiveSource {
  key: string
  name: string
  url: string
  epg?: string | null
  ua?: string | null
  headers?: Record<string, string>
  inline?: boolean
  channelCount?: number | null
}

export interface LiveChannel {
  id: string
  name: string
  url: string
  group?: string | null
  logo?: string | null
  tvgId?: string | null
  headers?: Record<string, string>
  unsupportedReason?: string
}

export interface LiveChannelsResponse {
  source?: string
  channels: LiveChannel[]
  epgUrl?: string
  error?: string
}

export interface LiveEpgResponse {
  source?: string
  epg?: string | null
  error?: string
}

export interface LiveProgramme {
  title: string
  start: number
  end: number
}

export type LiveSchedule = Map<string, LiveProgramme[]>
