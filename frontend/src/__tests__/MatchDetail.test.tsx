import { describe, it, expect } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.ts'
import MatchDetail from '../pages/MatchDetail.tsx'

const renderAt = (id: number) => render(
  <MemoryRouter initialEntries={[`/matches/${id}`]}>
    <Routes><Route path="/matches/:id" element={<MatchDetail />} /></Routes>
  </MemoryRouter>,
)

describe('MatchDetail', () => {
  it('shows posting info', async () => {
    server.use(http.get('/api/matches/1', () => HttpResponse.json({
      id: 1, llmScore: 85, cosineSimilarity: 0.78,
      reasoning: '{"strengths":["Kotlin"],"gaps":[],"summary":"strong"}',
      posting: { id: 10, title: 'Backend', company: 'Acme', requirements: 'Kotlin', contactEmail: 'jobs@acme.com' },
    })))
    renderAt(1)
    await waitFor(() => expect(screen.getByText('Backend')).toBeInTheDocument())
    expect(screen.getAllByText(/Kotlin/).length).toBeGreaterThan(0)
  })

  it('clicking Draft fetches a draft and shows editor', async () => {
    server.use(
      http.get('/api/matches/1', () => HttpResponse.json({
        id: 1, posting: { title: 'Backend', contactEmail: 'jobs@acme.com' },
      })),
      http.post('/api/matches/1/draft', () => HttpResponse.json({
        subject: 'Application for Backend',
        body: 'I would like to apply.',
      })),
    )
    renderAt(1)
    await waitFor(() => expect(screen.getByText('Backend')).toBeInTheDocument())
    fireEvent.click(screen.getByText(/draft/i))
    await waitFor(() => expect(screen.getByDisplayValue('Application for Backend')).toBeInTheDocument())
    expect(screen.getByDisplayValue('I would like to apply.')).toBeInTheDocument()
  })

  it('clicking Send posts subject + body', async () => {
    let captured: unknown
    server.use(
      http.get('/api/matches/1', () => HttpResponse.json({
        id: 1, posting: { title: 'Backend', contactEmail: 'jobs@acme.com' },
      })),
      http.post('/api/matches/1/draft', () => HttpResponse.json({ subject: 'S', body: 'B' })),
      http.post('/api/matches/1/send', async ({ request }) => {
        captured = await request.json()
        return new HttpResponse(null, { status: 200 })
      }),
    )
    renderAt(1)
    await waitFor(() => screen.getByText('Backend'))
    fireEvent.click(screen.getByText(/draft/i))
    await waitFor(() => screen.getByDisplayValue('S'))
    fireEvent.click(screen.getByText(/^send$/i))
    await waitFor(() => expect(captured).toEqual({ subject: 'S', body: 'B' }))
  })
})
