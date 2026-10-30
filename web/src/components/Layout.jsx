import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { NavLink, Outlet } from 'react-router'
import { CAN_IMPORT, CAN_MANAGE_USERS, ROLE_LABELS } from '../auth/roles'
import useAuth from '../auth/useAuth'

const NAV_LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/items', label: 'Items' },
  { to: '/alerts', label: 'Alerts' },
  { to: '/import', label: 'Import', roles: CAN_IMPORT },
  { to: '/users', label: 'Users', roles: CAN_MANAGE_USERS },
]

function Layout() {
  const { user, logout } = useAuth()
  const visibleLinks = NAV_LINKS.filter(({ roles }) => !roles || roles.includes(user.role))

  return (
    <>
      <AppBar position="static">
        <Toolbar sx={{ gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="h6" component="span" sx={{ mr: 2 }}>
            InventoryApp
          </Typography>
          <Box component="nav" aria-label="Main" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {visibleLinks.map(({ to, label, end }) => (
              <Button
                key={to}
                component={NavLink}
                to={to}
                end={end}
                color="inherit"
                sx={{ '&.active': { textDecoration: 'underline', textUnderlineOffset: 6 } }}
              >
                {label}
              </Button>
            ))}
          </Box>
          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography component="span" variant="body2">
              {user.username} ({ROLE_LABELS[user.role]})
            </Typography>
            <Button color="inherit" variant="outlined" onClick={logout}>
              Sign out
            </Button>
          </Box>
        </Toolbar>
      </AppBar>
      <Container component="main" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </>
  )
}

export default Layout
