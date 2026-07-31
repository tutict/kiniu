export type RequestFailureKind = 'unauthorized' | 'forbidden' | 'other'

export function requestFailureStatus(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined
  const candidate = error as {
    status?: unknown
    statusCode?: unknown
    response?: { status?: unknown }
  }
  const status = candidate.statusCode ?? candidate.status ?? candidate.response?.status
  return typeof status === 'number' ? status : undefined
}

export function classifyRequestFailure(error: unknown): RequestFailureKind {
  const status = requestFailureStatus(error)
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  return 'other'
}
