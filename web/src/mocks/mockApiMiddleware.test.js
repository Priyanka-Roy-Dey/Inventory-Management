// @vitest-environment node
import { createServer } from 'node:http'
import { afterEach, describe, expect, it } from 'vitest'
import { handlers } from './handlers'
import { createMockApiMiddleware } from './mockApiMiddleware'

let server

function startServer() {
  const middleware = createMockApiMiddleware(handlers)
  server = createServer((req, res) => {
    middleware(req, res, () => {
      res.statusCode = 404
      res.end('passed through')
    })
  })
  return new Promise((resolve) => {
    server.listen(0, () => resolve(`http://localhost:${server.address().port}`))
  })
}

afterEach(() => {
  server?.close()
})

describe('createMockApiMiddleware', () => {
  it('answers an API request with the mock handler response', async () => {
    const baseUrl = await startServer()

    const response = await fetch(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    })

    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toContain('application/json')
    expect((await response.json()).user).toEqual({ username: 'admin', role: 'ADMIN' })
  })

  it('returns the handler error status', async () => {
    const baseUrl = await startServer()

    const response = await fetch(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'wrong' }),
    })

    expect(response.status).toBe(401)
  })

  it('passes non-API requests on to the next middleware', async () => {
    const baseUrl = await startServer()

    const response = await fetch(`${baseUrl}/login`)

    expect(await response.text()).toBe('passed through')
  })

  it('passes API requests with no mock handler on to the next middleware', async () => {
    const baseUrl = await startServer()

    const response = await fetch(`${baseUrl}/api/unknown`)

    expect(await response.text()).toBe('passed through')
  })
})
