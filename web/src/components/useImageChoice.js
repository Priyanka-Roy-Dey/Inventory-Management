import { useEffect, useState } from 'react'

// The same limits as the API (A36).
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_IMAGE_BYTES = 2 * 1024 * 1024

function imageProblem(file) {
  if (!IMAGE_TYPES.includes(file.type)) {
    return 'Choose a JPEG, PNG or WebP image'
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return 'The image must be 2 MB or smaller'
  }
  return null
}

/**
 * Holds the image the user picked (with a preview URL) or why it was refused. Uploading happens elsewhere.
 */
function useImageChoice() {
  const [image, setImage] = useState(null)
  const [error, setError] = useState(null)

  // Frees the preview's memory when it is replaced or the form closes.
  useEffect(() => {
    return () => {
      if (image) {
        URL.revokeObjectURL(image.previewUrl)
      }
    }
  }, [image])

  function choose(event) {
    const file = event.target.files[0]
    // Clear the input so choosing the same file again still triggers a change.
    event.target.value = ''
    if (!file) {
      return
    }
    const problem = imageProblem(file)
    setError(problem)
    setImage(problem ? null : { file, previewUrl: URL.createObjectURL(file) })
  }

  function clear() {
    setImage(null)
    setError(null)
  }

  return { image, error, choose, clear }
}

export default useImageChoice
