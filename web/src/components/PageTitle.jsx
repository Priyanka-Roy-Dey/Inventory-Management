import { Typography } from '@mui/material'

function PageTitle({ children }) {
  return (
    <Typography variant="h4" component="h1" gutterBottom>
      {children}
    </Typography>
  )
}

export default PageTitle
