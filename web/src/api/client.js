export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function readJson(response) {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export async function apiRequest(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  // FormData (file uploads) sets its own multipart Content-Type with the boundary.
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(new URL(`/api${path}`, window.location.origin), {
    method,
    headers,
    body: body === undefined || isForm ? body : JSON.stringify(body),
  })
  const data = await readJson(response)

  if (!response.ok) {
    throw new ApiError(response.status, data?.message ?? `Request failed with status ${response.status}`)
  }
  return data
}
