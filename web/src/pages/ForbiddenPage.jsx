import { Button, Typography } from '@mui/material'
import { Link } from 'react-router'
import PageTitle from '../components/PageTitle'

function ForbiddenPage() {
  return (
    <>
      <PageTitle>Access denied</PageTitle>
      <Typography sx={{ mb: 2 }}>Your role does not have access to this page.</Typography>
      <Button component={Link} to="/" variant="contained">
        Back to dashboard
      </Button>
    </>
  )
}

export default ForbiddenPage
