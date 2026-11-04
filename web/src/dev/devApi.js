import { handlers } from '../mocks/handlers'
import { createMockApiMiddleware } from '../mocks/mockApiMiddleware'

const DEFAULT_API_URL = 'http://localhost:8080'

const mockApi = {
  name: 'mock-api',
  apply: 'serve',
  configureServer(server) {
    server.middlewares.use(createMockApiMiddleware(handlers))
  },
}

// `npm run dev` sends /api to the real API; `npm run dev:mock` answers it from the mock handlers instead.
export function devApi(mode, env) {
  if (mode === 'mock') {
    return { plugins: [mockApi], server: {} }
  }
  return {
    plugins: [],
    server: { proxy: { '/api': { target: env.VITE_API_URL || DEFAULT_API_URL } } },
  }
}
