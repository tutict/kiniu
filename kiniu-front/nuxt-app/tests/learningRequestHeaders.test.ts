import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildLearningRequestHeaders,
  type LearningRequestHeaderConfig,
  type LearningRequestKind
} from '../utils/learningRequestHeaders.ts'

const config: LearningRequestHeaderConfig = {
  localToken: ' local-token ',
  providerUrl: ' https://provider.example/v1 ',
  apiKey: ' provider-secret ',
  model: ' model-x '
}

test('non-feedback learning requests only carry the local access token', () => {
  const localOnlyKinds: LearningRequestKind[] = ['catalog', 'progress', 'check', 'publish']
  for (const kind of localOnlyKinds) {
    assert.deepEqual(buildLearningRequestHeaders(kind, config), {
      'X-Local-Token': 'local-token'
    })
  }
})

test('mentor feedback carries local access and provider configuration', () => {
  assert.deepEqual(buildLearningRequestHeaders('feedback', config), {
    'X-Local-Token': 'local-token',
    Authorization: 'Bearer provider-secret',
    'X-API-Key': 'provider-secret',
    'X-Provider-Url': 'https://provider.example/v1',
    'X-Model': 'model-x'
  })
})
