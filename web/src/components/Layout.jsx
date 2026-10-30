import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { NavLink, Outlet } from 'react-router'

const NAV_LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/items', label: 'Items' },
  { to: '/alerts', label: 'Alerts' },
  { to: '/import', label: 'Import' },
  { to: '/users', label: 'Users' },
]

function Layout() {
  return (
    <>
      <AppBar position="static">
        <Toolbar sx={{ gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="h6" component="span" sx={{ mr: 2 }}>
            InventoryApp
          </Typography>
          <Box component="nav" aria-label="Main" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {NAV_LINKS.map(({ to, label, end }) => (
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
        </Toolbar>
      </AppBar>
      <Container component="main" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </>
  )
}

export default Layout
