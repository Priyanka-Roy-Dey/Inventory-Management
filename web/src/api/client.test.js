import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { ApiError, apiRequest } from './client'

describe('apiRequest', () => {
  it('returns the parsed JSON body', async () => {
    server.use(http.get('/api/items', () => HttpResponse.json([{ sku: 'A-1' }])))

    await expect(apiRequest('/items')).resolves.toEqual([{ sku: 'A-1' }])
  })

  it('sends a JSON body', async () => {
    let received
    server.use(
      http.post('/api/items', async ({ request }) => {
        received = { contentType: request.headers.get('Content-Type'), body: await request.json() }
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )

    await apiRequest('/items', { method: 'POST', body: { sku: 'A-1' } })

    expect(received).toEqual({ contentType: 'application/json', body: { sku: 'A-1' } })
  })

  it('sends the bearer token when given', async () => {
    let authorization
    server.use(
      http.get('/api/items', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json([])
      }),
    )

    await apiRequest('/items', { token: 'abc' })

    expect(authorization).toBe('Bearer abc')
  })

  it('returns null for an empty response', async () => {
    server.use(http.delete('/api/items/1', () => new HttpResponse(null, { status: 204 })))

    await expect(apiRequest('/items/1', { method: 'DELETE' })).resolves.toBeNull()
  })

  it('throws an ApiError with the status and server message', async () => {
    server.use(
      http.post('/api/items', () =>
        HttpResponse.json({ message: 'SKU already exists' }, { status: 409 }),
      ),
    )

    const error = await apiRequest('/items', { method: 'POST', body: {} }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.message).toBe('SKU already exists')
  })

  it('throws an ApiError with a fallback message when the body is not JSON', async () => {
    server.use(http.get('/api/items', () => new HttpResponse('oops', { status: 500 })))

    const error = await apiRequest('/items').catch((e) => e)

    expect(error.status).toBe(500)
    expect(error.message).toBe('Request failed with status 500')
  })
})
