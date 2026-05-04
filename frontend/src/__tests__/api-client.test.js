import { describe, it, expect } from 'vitest'
import { server, http, HttpResponse } from '../test-setup.js'
import {
  listMatches,
  getMatch,
  draftMatch,
  sendMatch,
  skipMatch,
  listCvs,
  uploadCv,
  getDashboard,
} from '../api/client.js'

describe('api client', () => {
  it('listMatches returns array of matches', async () => {
    server.use(http.get('/api/matches', () => HttpResponse.json([
      { id: 1, llmScore: 80, posting: { title: 'Backend' } },
    ])))
    const matches = await listMatches()
    expect(matches).toHaveLength(1)
    expect(matches[0].id).toBe(1)
  })

  it('getMatch returns single match', async () => {
    server.use(http.get('/api/matches/42', () => HttpResponse.json({ id: 42 })))
    const match = await getMatch(42)
    expect(match.id).toBe(42)
  })

  it('draftMatch posts to /draft', async () => {
    server.use(http.post('/api/matches/7/draft', () => HttpResponse.json({ subject: 'S', body: 'B' })))
    const drafted = await draftMatch(7)
    expect(drafted.subject).toBe('S')
  })

  it('sendMatch posts subject and body', async () => {
    let receivedBody
    server.use(http.post('/api/matches/9/send', async ({ request }) => {
      receivedBody = await request.json()
      return new HttpResponse(null, { status: 200 })
    }))
    await sendMatch(9, { subject: 'X', body: 'Y' })
    expect(receivedBody).toEqual({ subject: 'X', body: 'Y' })
  })

  it('skipMatch posts to /skip', async () => {
    let called = false
    server.use(http.post('/api/matches/3/skip', () => {
      called = true
      return new HttpResponse(null, { status: 200 })
    }))
    await skipMatch(3)
    expect(called).toBe(true)
  })

  it('uploadCv sends multipart', async () => {
    server.use(http.post('/api/cv', () => HttpResponse.json({
      id: 1, label: 'default', parsedTextLength: 100, skills: [],
    })))
    const file = new File(['pdf'], 'cv.pdf', { type: 'application/pdf' })
    const result = await uploadCv(file, 'default')
    expect(result.id).toBe(1)
  })

  it('listCvs returns array', async () => {
    server.use(http.get('/api/cv', () => HttpResponse.json([])))
    expect(await listCvs()).toEqual([])
  })

  it('getDashboard returns object', async () => {
    server.use(http.get('/api/admin/queue/counts', () => HttpResponse.json({ INGESTED: 1, MATCHED: 5 })))
    expect(await getDashboard()).toEqual({ INGESTED: 1, MATCHED: 5 })
  })
})
