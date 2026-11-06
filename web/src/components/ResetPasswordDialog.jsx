import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'
import { useState } from 'react'

/**
 * Asks an Admin to confirm a password reset. `onConfirm` throws an error with a message on failure.
 */
function ResetPasswordDialog({ user, onConfirm, onClose }) {
  const [error, setError] = useState('')
  const [resetting, setResetting] = useState(false)

  async function handleReset() {
    setError('')
    setResetting(true)
    try {
      await onConfirm()
    } catch (e) {
      setError(e.message)
      setResetting(false)
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="reset-password-title">
      <DialogTitle id="reset-password-title">Reset password for {user.username}?</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 1 }}>
            {error}
          </Alert>
        )}
        <DialogContentText>
          {user.username} gets a new temporary password, is signed out everywhere, and must choose a new password at
          the next sign-in.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="warning" variant="contained" onClick={handleReset} disabled={resetting}>
          Reset password
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ResetPasswordDialog
