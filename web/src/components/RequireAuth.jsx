import { Navigate, Outlet, useLocation } from 'react-router'
import useAuth from '../auth/useAuth'
import ForbiddenPage from '../pages/ForbiddenPage'

function RequireAuth({ roles }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  // The API refuses every other call until the temporary password is replaced.
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (roles && !roles.includes(user.role)) {
    return <ForbiddenPage />
  }
  return <Outlet />
}

export default RequireAuth
