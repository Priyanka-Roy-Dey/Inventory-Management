import { useContext } from 'react'
import AuthContext from './AuthContext'

function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return auth
}

export default useAuth
