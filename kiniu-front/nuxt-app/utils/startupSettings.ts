export type StartupSettingsInput = {
  runtimeBackendUrl: unknown
  runtimeLocalToken: unknown
  storedBackendUrl: string
  sessionLocalToken: string
}

export type StartupSettings = {
  backendUrl: string
  localToken: string
  bootstrappedToken: boolean
}

function normalizedString(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

export function resolveStartupSettings(input: StartupSettingsInput): StartupSettings {
  const runtimeBackendUrl = normalizedString(input.runtimeBackendUrl)
  const runtimeLocalToken = normalizedString(input.runtimeLocalToken)
  return {
    backendUrl: runtimeBackendUrl || input.storedBackendUrl.trim(),
    localToken: runtimeLocalToken || input.sessionLocalToken.trim(),
    bootstrappedToken: !!runtimeLocalToken
  }
}
