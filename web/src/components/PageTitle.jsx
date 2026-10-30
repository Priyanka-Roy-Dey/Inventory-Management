import { Typography } from '@mui/material'

function PageTitle({ children, align }) {
  return (
    <Typography variant="h4" component="h1" align={align} gutterBottom>
      {children}
    </Typography>
  )
}

export default PageTitle
