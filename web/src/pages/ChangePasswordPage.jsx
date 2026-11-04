import { Alert, Box, Button, Container, Paper, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import useAuth from '../auth/useAuth'
import PageTitle from '../components/PageTitle'

// Matches the API's rule; 72 is the longest password BCrypt can hash.
const MIN_LENGTH = 12
const MAX_LENGTH = 72

function validate({ currentPassword, newPassword, confirmPassword }) {
  const errors = {}
  if (!currentPassword) {
    errors.currentPassword = 'Current password is required'
  }
  if (!newPassword) {
    errors.newPassword = 'New password is required'
  } else if (newPassword.length < MIN_LENGTH) {
    errors.newPassword = `Use at least ${MIN_LENGTH} characters`
  } else if (newPassword.length > MAX_LENGTH) {
    errors.newPassword = `Use at most ${MAX_LENGTH} characters`
  }
  if (confirmPassword !== newPassword) {
    errors.confirmPassword = 'Passwords do not match'
  }
  return errors
}

function ChangePasswordPage() {
  const { user, changePassword } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(event) {
    setValues({ ...values, [event.target.name]: event.target.value })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const newErrors = validate(values)
    setErrors(newErrors)
    setServerError('')
    if (Object.keys(newErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await changePassword(values.currentPassword, values.newPassword)
      navigate('/', { replace: true })
    } catch (error) {
      setServerError(error.message)
      setSubmitting(false)
    }
  }

  return (
    <Container component="main" maxWidth="xs" sx={{ py: 8 }}>
      <Paper sx={{ p: 4 }}>
        <PageTitle align="center">Set a new password</PageTitle>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {user.mustChangePassword
            ? 'You signed in with a temporary password. Choose your own password to continue.'
            : 'Choose a new password.'}
        </Typography>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <Box component="form" noValidate onSubmit={handleSubmit}>
          <TextField
            label="Current password"
            name="currentPassword"
            type="password"
            autoComplete="current-password"
            value={values.currentPassword}
            onChange={handleChange}
            error={Boolean(errors.currentPassword)}
            helperText={errors.currentPassword}
            required
            fullWidth
            margin="normal"
            autoFocus
          />
          <TextField
            label="New password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            value={values.newPassword}
            onChange={handleChange}
            error={Boolean(errors.newPassword)}
            helperText={errors.newPassword ?? `${MIN_LENGTH} to ${MAX_LENGTH} characters`}
            required
            fullWidth
            margin="normal"
          />
          <TextField
            label="Confirm new password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={values.confirmPassword}
            onChange={handleChange}
            error={Boolean(errors.confirmPassword)}
            helperText={errors.confirmPassword}
            required
            fullWidth
            margin="normal"
          />
          <Button type="submit" variant="contained" fullWidth disabled={submitting} sx={{ mt: 2 }}>
            Change password
          </Button>
        </Box>
      </Paper>
    </Container>
  )
}

export default ChangePasswordPage
