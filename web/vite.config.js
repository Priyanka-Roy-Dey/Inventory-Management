import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'
import { handlers } from './src/mocks/handlers'
import { createMockApiMiddleware } from './src/mocks/mockApiMiddleware'

// Answers /api/* from the mock handlers during `npm run dev` only, until the real API exists.
const mockApi = {
  name: 'mock-api',
  apply: 'serve',
  configureServer(server) {
    server.middlewares.use(createMockApiMiddleware(handlers))
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    mockApi,
  ],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
})
