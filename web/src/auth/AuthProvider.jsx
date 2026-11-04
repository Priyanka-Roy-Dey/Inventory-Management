import { useRef, useState } from 'react'
import { apiRequest } from '../api/client'
import AuthContext from './AuthContext'

const SESSION_KEY = 'inventory.session'

function readSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY))
  } catch {
    return null
  }
}

function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)
  // Requests read the latest tokens from here, even before React re-renders.
  const sessionRef = useRef(session)
  const refreshing = useRef(null)

  function saveSession(newSession) {
    sessionRef.current = newSession
    if (newSession) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(newSession))
    } else {
      sessionStorage.removeItem(SESSION_KEY)
    }
    setSession(newSession)
  }

  async function login(username, password) {
    saveSession(await apiRequest('/auth/login', { method: 'POST', body: { username, password } }))
  }

  function logout() {
    saveSession(null)
  }

  // A refresh token works only once, so requests that fail together wait for the same refresh.
  function refreshSession() {
    if (!refreshing.current) {
      refreshing.current = apiRequest('/auth/refresh', {
        method: 'POST',
        body: { refreshToken: sessionRef.current?.refreshToken },
      })
        .then(saveSession)
        .finally(() => {
          refreshing.current = null
        })
    }
    return refreshing.current
  }

  // Calls the API as the signed-in user. An expired access token is refreshed once and the call retried.
  async function request(path, options = {}) {
    const token = sessionRef.current?.accessToken
    try {
      return await apiRequest(path, { ...options, token })
    } catch (error) {
      if (error.status !== 401 || !sessionRef.current?.refreshToken) {
        throw error
      }
      if (token === sessionRef.current.accessToken) {
        try {
          await refreshSession()
        } catch {
          logout()
          throw error
        }
      }
      return apiRequest(path, { ...options, token: sessionRef.current.accessToken })
    }
  }

  async function changePassword(currentPassword, newPassword) {
    saveSession(
      await request('/auth/change-password', {
        method: 'POST',
        body: { currentPassword, newPassword },
      }),
    )
  }

  const value = {
    user: session?.user ?? null,
    token: session?.accessToken ?? null,
    login,
    logout,
    request,
    changePassword,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
