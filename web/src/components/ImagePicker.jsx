import { Box, Button, Typography } from '@mui/material'
import { IMAGE_TYPES } from './useImageChoice'

/**
 * File picker with a preview, driven by `useImageChoice`. Only checks the file; the form uploads it on save.
 */
function ImagePicker({ choice, hint }) {
  const { image, error, choose, clear } = choice
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', mt: 1 }}>
      <Button component="label" variant="outlined" size="small">
        {image ? 'Choose another image' : 'Choose image'}
        <input hidden type="file" accept={IMAGE_TYPES.join(',')} aria-label="Image (optional)" onChange={choose} />
      </Button>
      {image && (
        <>
          <Box
            component="img"
            src={image.previewUrl}
            alt={`Preview of ${image.file.name}`}
            sx={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 1 }}
          />
          <Button size="small" aria-label="Remove chosen image" onClick={clear}>
            Remove
          </Button>
        </>
      )}
      <Typography variant="body2" color={error ? 'error' : 'text.secondary'}>
        {error ?? hint}
      </Typography>
    </Box>
  )
}

export default ImagePicker
