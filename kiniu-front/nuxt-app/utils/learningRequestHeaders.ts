export type LearningRequestKind = 'catalog' | 'progress' | 'check' | 'publish' | 'feedback'

export type LearningRequestHeaderConfig = {
  localToken: string
  providerUrl: string
  apiKey: string
  model: string
}

export function buildLearningRequestHeaders(
  kind: LearningRequestKind,
  config: LearningRequestHeaderConfig
): Record<string, string> {
  const headers: Record<string, string> = {}
  const localToken = config.localToken.trim()
  if (localToken) headers['X-Local-Token'] = localToken

  if (kind !== 'feedback') return headers

  const apiKey = config.apiKey.trim()
  if (apiKey) {
    headers.Authorization = `Bearer ${apiKey}`
    headers['X-API-Key'] = apiKey
  }
  const providerUrl = config.providerUrl.trim()
  const model = config.model.trim()
  if (providerUrl) headers['X-Provider-Url'] = providerUrl
  if (model) headers['X-Model'] = model
  return headers
}
