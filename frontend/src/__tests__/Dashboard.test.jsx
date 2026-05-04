import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import Dashboard from '../pages/Dashboard.jsx'

describe('Dashboard', () => {
  it('renders queue counts', async () => {
    server.use(http.get('/api/admin/queue/counts', () => HttpResponse.json({
      INGESTED: 1, PARSED: 2, EMBEDDED: 0, MATCHED: 5, FAILED: 0, IRRELEVANT: 3, OUT_OF_SCOPE: 4, CLASSIFIED: 1,
    })))
    render(<MemoryRouter><Dashboard /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument())
    expect(screen.getByText(/MATCHED/)).toBeInTheDocument()
  })
})
