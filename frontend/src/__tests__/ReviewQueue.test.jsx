import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import ReviewQueue from '../pages/ReviewQueue.jsx'

describe('ReviewQueue', () => {
  it('renders the loading state initially', () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('renders matches when loaded', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([
      {
        id: 1, llmScore: 85, cosineSimilarity: 0.78,
        posting: { title: 'Backend Engineer', company: 'Acme', location: 'Tel Aviv', contactEmail: 'jobs@acme.com' },
      },
      {
        id: 2, llmScore: 70, cosineSimilarity: 0.65,
        posting: { title: 'DevOps', company: 'Beta', location: 'Remote', contactEmail: 'careers@beta.com' },
      },
    ])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument())
    expect(screen.getByText('DevOps')).toBeInTheDocument()
    expect(screen.getByText(/85/)).toBeInTheDocument()
  })

  it('shows empty state when no matches', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([])))
    render(<MemoryRouter><ReviewQueue /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText(/no matches/i)).toBeInTheDocument())
  })
})
