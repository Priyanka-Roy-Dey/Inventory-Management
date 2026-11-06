import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { useState } from 'react'

/**
 * Shows a temporary password the API returned. It is not stored anywhere, so this is the only chance to copy it.
 */
function TemporaryPasswordDialog({ username, password, onClose }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(password)
      setCopied(true)
    } catch {
      // Clipboard access can be blocked; the password is still on screen to copy by hand.
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="temporary-password-title" fullWidth maxWidth="xs">
      <DialogTitle id="temporary-password-title">Temporary password for {username}</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>
          Give this to {username} now. It will not be shown again. They must choose their own password when they
          sign in.
        </Alert>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography component="code" sx={{ fontFamily: 'monospace', fontSize: '1.1rem', wordBreak: 'break-all' }}>
            {password}
          </Typography>
          <Button size="small" variant="outlined" onClick={copy}>
            Copy
          </Button>
          {copied && <Typography color="success.main">Copied</Typography>}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={onClose}>
          Done
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default TemporaryPasswordDialog
