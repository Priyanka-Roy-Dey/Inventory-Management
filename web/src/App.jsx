import { Route, Routes } from 'react-router'
import Layout from './components/Layout'
import AlertsPage from './pages/AlertsPage'
import DashboardPage from './pages/DashboardPage'
import ImportPage from './pages/ImportPage'
import ItemsPage from './pages/ItemsPage'
import NotFoundPage from './pages/NotFoundPage'
import UsersPage from './pages/UsersPage'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="items" element={<ItemsPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="import" element={<ImportPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
