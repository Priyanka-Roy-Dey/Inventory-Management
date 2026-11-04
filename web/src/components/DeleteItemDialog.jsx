import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'
import { useState } from 'react'

/**
 * Asks an Admin to confirm a soft delete. `onConfirm` throws an error with a message on failure.
 */
function DeleteItemDialog({ item, onConfirm, onClose }) {
  const [error, setError] = useState('')
  const [deleting, setDeleting] = useState(false)

  async function handleDelete() {
    setError('')
    setDeleting(true)
    try {
      await onConfirm()
    } catch (e) {
      setError(e.message)
      setDeleting(false)
    }
  }

  return (
    <Dialog open onClose={onClose} aria-labelledby="delete-item-title">
      <DialogTitle id="delete-item-title">Delete {item.sku}?</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 1 }}>
            {error}
          </Alert>
        )}
        <DialogContentText>
          {item.name} disappears from the list, and its SKU can&apos;t be reused for a new item.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" variant="contained" onClick={handleDelete} disabled={deleting}>
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default DeleteItemDialog
