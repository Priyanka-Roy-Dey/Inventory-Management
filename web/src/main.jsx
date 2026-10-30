import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.jsx'
import AuthProvider from './auth/AuthProvider'

const theme = createTheme()

async function startMockApi() {
  if (!import.meta.env.DEV) {
    return
  }
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

function warnMockApiFailed(error) {
  console.warn('Mock API did not start; API calls will fail.', error)
}

startMockApi().catch(warnMockApiFailed).finally(() => {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </StrictMode>,
  )
})
