import { Route, Routes } from 'react-router'
import { CAN_IMPORT, CAN_MANAGE_USERS } from './auth/roles'
import Layout from './components/Layout'
import RequireAuth from './components/RequireAuth'
import AlertsPage from './pages/AlertsPage'
import DashboardPage from './pages/DashboardPage'
import ImportPage from './pages/ImportPage'
import ItemsPage from './pages/ItemsPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import UsersPage from './pages/UsersPage'

function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="items" element={<ItemsPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route element={<RequireAuth roles={CAN_IMPORT} />}>
            <Route path="import" element={<ImportPage />} />
          </Route>
          <Route element={<RequireAuth roles={CAN_MANAGE_USERS} />}>
            <Route path="users" element={<UsersPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
