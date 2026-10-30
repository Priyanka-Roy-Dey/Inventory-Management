import { getResponse } from 'msw'
import { Readable } from 'node:stream'

function toFetchRequest(req) {
  const hasBody = req.method !== 'GET' && req.method !== 'HEAD'
  return new Request(new URL(req.url, `http://${req.headers.host}`), {
    method: req.method,
    headers: req.headers,
    body: hasBody ? Readable.toWeb(req) : undefined,
    duplex: 'half',
  })
}

async function sendResponse(response, res) {
  res.statusCode = response.status
  response.headers.forEach((value, key) => res.setHeader(key, value))
  res.end(await response.text())
}

export function createMockApiMiddleware(handlers) {
  return async (req, res, next) => {
    if (!req.url.startsWith('/api/')) {
      next()
      return
    }
    const response = await getResponse(handlers, toFetchRequest(req))
    if (!response) {
      next()
      return
    }
    await sendResponse(response, res)
  }
}
