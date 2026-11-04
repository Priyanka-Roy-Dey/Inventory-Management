import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig, loadEnv } from 'vite'
import { devApi } from './src/dev/devApi'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const api = devApi(mode, loadEnv(mode, import.meta.dirname))
  return {
    plugins: [react(), babel({ presets: [reactCompilerPreset()] }), ...api.plugins],
    server: api.server,
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
    },
  }
})
