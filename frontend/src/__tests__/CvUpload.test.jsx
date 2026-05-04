import { describe, it, expect } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server, http, HttpResponse } from '../test-setup.js'
import CvUpload from '../pages/CvUpload.jsx'

describe('CvUpload', () => {
  it('shows existing CVs', async () => {
    server.use(http.get('/api/cv', () => HttpResponse.json([
      { id: 1, label: 'default', fileName: 'cv.pdf', isActive: true, createdAt: '2026-04-29T10:00:00Z' },
    ])))
    render(<MemoryRouter><CvUpload /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('cv.pdf')).toBeInTheDocument())
  })

  it('uploads a file and refreshes list', async () => {
    let listCalls = 0
    server.use(
      http.get('/api/cv', () => { listCalls++; return HttpResponse.json([]) }),
      http.post('/api/cv', () => HttpResponse.json({ id: 5, label: 'default', parsedTextLength: 100, skills: [] })),
    )
    render(<MemoryRouter><CvUpload /></MemoryRouter>)
    await waitFor(() => expect(listCalls).toBeGreaterThan(0))

    const file = new File(['pdf'], 'me.pdf', { type: 'application/pdf' })
    const input = screen.getByLabelText(/file/i)
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByText(/upload/i))

    await waitFor(() => expect(listCalls).toBeGreaterThan(1))
  })
})
