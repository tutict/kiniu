import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveStartupSettings } from '../utils/startupSettings.ts'

test('runtime startup values override stale browser settings', () => {
  assert.deepEqual(resolveStartupSettings({
    runtimeBackendUrl: ' http://127.0.0.1:18080 ',
    runtimeLocalToken: ' generated-token ',
    storedBackendUrl: 'http://localhost:8080',
    sessionLocalToken: 'stale-token'
  }), {
    backendUrl: 'http://127.0.0.1:18080',
    localToken: 'generated-token',
    bootstrappedToken: true
  })
})

test('browser settings remain available without startup injection', () => {
  assert.deepEqual(resolveStartupSettings({
    runtimeBackendUrl: '',
    runtimeLocalToken: '',
    storedBackendUrl: 'http://localhost:8080',
    sessionLocalToken: 'session-token'
  }), {
    backendUrl: 'http://localhost:8080',
    localToken: 'session-token',
    bootstrappedToken: false
  })
})
