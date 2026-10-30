import { Alert, Box, Button, Container, Paper, TextField } from '@mui/material'
import { blue } from '@mui/material/colors'
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import useAuth from '../auth/useAuth'
import PageTitle from '../components/PageTitle'

const WHITE_INPUT = { '& .MuiOutlinedInput-root': { bgcolor: 'common.white' } }

function validate({ username, password }) {
  const errors = {}
  if (!username.trim()) {
    errors.username = 'Username is required'
  }
  if (!password) {
    errors.password = 'Password is required'
  }
  return errors
}

function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [values, setValues] = useState({ username: '', password: '' })
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const returnTo = location.state?.from ?? '/'

  if (user) {
    return <Navigate to={returnTo} replace />
  }

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
      await login(values.username.trim(), values.password)
      navigate(returnTo, { replace: true })
    } catch (error) {
      setServerError(error.message)
      setSubmitting(false)
    }
  }

  return (
    <Container component="main" maxWidth="xs" sx={{ py: 8 }}>
      <Box component="h1" sx={{ textAlign: 'center' }}>
        Inventory Management
      </Box>
      <Paper sx={{ p: 4, bgcolor: blue[50] }}>
        <PageTitle align="center">Sign in</PageTitle>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <Box component="form" noValidate onSubmit={handleSubmit}>
          <TextField
            label="Username"
            name="username"
            autoComplete="username"
            value={values.username}
            onChange={handleChange}
            error={Boolean(errors.username)}
            helperText={errors.username}
            required
            fullWidth
            margin="normal"
            sx={WHITE_INPUT}
            autoFocus
          />
          <TextField
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={values.password}
            onChange={handleChange}
            error={Boolean(errors.password)}
            helperText={errors.password}
            required
            fullWidth
            margin="normal"
            sx={WHITE_INPUT}
          />
          <Button type="submit" variant="contained" fullWidth disabled={submitting} sx={{ mt: 2 }}>
            Sign in
          </Button>
        </Box>
      </Paper>
    </Container>
  )
}

export default LoginPage
