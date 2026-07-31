import assert from 'node:assert/strict'
import test from 'node:test'
import { classifyRequestFailure } from '../utils/requestFailure.ts'

test('classifies authentication and origin failures from fetch errors', () => {
  assert.equal(classifyRequestFailure({ statusCode: 401 }), 'unauthorized')
  assert.equal(classifyRequestFailure({ response: { status: 403 } }), 'forbidden')
})

test('keeps unrelated failures on the generic path', () => {
  assert.equal(classifyRequestFailure(new Error('connection refused')), 'other')
})
