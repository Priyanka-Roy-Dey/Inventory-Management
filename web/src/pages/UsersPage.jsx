import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useState } from 'react'
import { ROLE_LABELS } from '../auth/roles'
import useAuth from '../auth/useAuth'
import CreateUserDialog from '../components/CreateUserDialog'
import PageTitle from '../components/PageTitle'
import ResetPasswordDialog from '../components/ResetPasswordDialog'
import TemporaryPasswordDialog from '../components/TemporaryPasswordDialog'

function byUsername(a, b) {
  return a.username.localeCompare(b.username)
}

function UsersTable({ users, currentUsername, onReset }) {
  return (
    <TableContainer component={Paper}>
      <Table aria-label="Users" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Username</TableCell>
            <TableCell>Role</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Created</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {users.map((user) => (
            <TableRow key={user.id}>
              <TableCell>{user.username}</TableCell>
              <TableCell>{ROLE_LABELS[user.role]}</TableCell>
              <TableCell>
                {user.mustChangePassword ? (
                  <Chip label="Must change password" color="warning" size="small" />
                ) : (
                  <Chip label="Active" color="success" size="small" variant="outlined" />
                )}
              </TableCell>
              <TableCell>{new Date(user.createdAt).toLocaleDateString()}</TableCell>
              <TableCell align="right">
                {/* Your own password is changed on the change-password page, not reset here. */}
                {user.username === currentUsername ? (
                  <Typography variant="body2" color="text.secondary">
                    You
                  </Typography>
                ) : (
                  <Button size="small" aria-label={`Reset password for ${user.username}`} onClick={() => onReset(user)}>
                    Reset password
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

/**
 * Admin-only (see RequireAuth in App). Users are created and reset through the API from INV-5.
 */
function UsersPage() {
  const { user, request } = useAuth()
  // `users` is always an array, so every read is safe whatever the status.
  const [state, setState] = useState({ status: 'loading', users: [] })
  const [attempt, setAttempt] = useState(0)
  const [creating, setCreating] = useState(false)
  const [resetting, setResetting] = useState(null)
  const [issued, setIssued] = useState(null)

  const fetchUsers = useEffectEvent(() => request('/users'))

  useEffect(() => {
    let ignore = false
    fetchUsers().then(
      (users) => !ignore && setState({ status: 'ready', users }),
      (error) => !ignore && setState({ status: 'error', message: error.message, users: [] }),
    )
    return () => {
      ignore = true
    }
  }, [attempt])

  function retry() {
    setState({ status: 'loading', users: [] })
    setAttempt(attempt + 1)
  }

  async function createUser(body) {
    const result = await request('/users', { method: 'POST', body })
    setState({ status: 'ready', users: [...state.users, result.user].sort(byUsername) })
    setCreating(false)
    setIssued({ username: result.user.username, password: result.temporaryPassword })
  }

  // Takes the user rather than reading `resetting`, which is null while no dialog is open.
  async function resetPassword(target) {
    const result = await request(`/users/${target.id}/reset-password`, { method: 'POST' })
    setState({ ...state, users: state.users.map((each) => (each.id === result.user.id ? result.user : each)) })
    setResetting(null)
    setIssued({ username: result.user.username, password: result.temporaryPassword })
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <PageTitle>Users</PageTitle>
        <Button variant="contained" onClick={() => setCreating(true)}>
          Create user
        </Button>
      </Box>
      {state.status === 'loading' && <CircularProgress aria-label="Loading users" />}
      {state.status === 'error' && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={retry}>
              Retry
            </Button>
          }
        >
          Could not load users: {state.message}
        </Alert>
      )}
      {state.status === 'ready' && state.users.length === 0 && <Typography>No users yet.</Typography>}
      {state.status === 'ready' && state.users.length > 0 && (
        <UsersTable users={state.users} currentUsername={user.username} onReset={setResetting} />
      )}
      {creating && <CreateUserDialog onCreate={createUser} onClose={() => setCreating(false)} />}
      {resetting && (
        <ResetPasswordDialog
          user={resetting}
          onConfirm={() => resetPassword(resetting)}
          onClose={() => setResetting(null)}
        />
      )}
      {issued && (
        <TemporaryPasswordDialog username={issued.username} password={issued.password} onClose={() => setIssued(null)} />
      )}
    </>
  )
}

export default UsersPage
