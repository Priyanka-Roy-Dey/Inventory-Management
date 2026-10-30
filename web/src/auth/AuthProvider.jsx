import { useState } from 'react'
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

  async function login(username, password) {
    const newSession = await apiRequest('/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(newSession))
    setSession(newSession)
  }

  function logout() {
    sessionStorage.removeItem(SESSION_KEY)
    setSession(null)
  }

  const value = {
    user: session?.user ?? null,
    token: session?.accessToken ?? null,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
