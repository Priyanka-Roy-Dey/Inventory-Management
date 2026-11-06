import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField } from '@mui/material'
import { useState } from 'react'
import { ROLE_LABELS } from '../auth/roles'

// The same rule as the API (CreateUserRequest).
const USERNAME = /^[A-Za-z0-9._-]{3,50}$/

function validate(username) {
  if (!username) {
    return 'Username is required'
  }
  return USERNAME.test(username) ? null : "Use 3–50 letters, digits, '.', '_' or '-'"
}

/**
 * Collects a new user's name and role. `onCreate` throws an error with a message on failure.
 */
function CreateUserDialog({ onCreate, onClose }) {
  const [username, setUsername] = useState('')
  const [role, setRole] = useState('STAFF')
  const [error, setError] = useState(null)
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    const trimmed = username.trim()
    const problem = validate(trimmed)
    setError(problem)
    setServerError('')
    if (problem) {
      return
    }
    setSubmitting(true)
    try {
      await onCreate({ username: trimmed, role })
    } catch (e) {
      setServerError(e.message)
      setSubmitting(false)
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="create-user-title" fullWidth maxWidth="xs">
      <Box component="form" noValidate onSubmit={handleSubmit}>
        <DialogTitle id="create-user-title">Create user</DialogTitle>
        <DialogContent>
          {serverError && (
            <Alert severity="error" sx={{ mb: 1 }}>
              {serverError}
            </Alert>
          )}
          <TextField
            label="Username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            error={Boolean(error)}
            helperText={error ?? 'They sign in with a temporary password and choose their own.'}
            required
            fullWidth
            margin="dense"
            autoFocus
          />
          <TextField
            select
            label="Role"
            value={role}
            onChange={(event) => setRole(event.target.value)}
            fullWidth
            margin="dense"
          >
            {Object.entries(ROLE_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            Create
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}

export default CreateUserDialog
