import { Button, Typography } from '@mui/material'
import { Link } from 'react-router'
import PageTitle from '../components/PageTitle'

function NotFoundPage() {
  return (
    <>
      <PageTitle>Page not found</PageTitle>
      <Typography sx={{ mb: 2 }}>The page you are looking for does not exist.</Typography>
      <Button component={Link} to="/" variant="contained">
        Back to dashboard
      </Button>
    </>
  )
}

export default NotFoundPage
