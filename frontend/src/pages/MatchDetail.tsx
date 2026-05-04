import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getMatch, draftMatch, sendMatch, skipMatch } from '../api/client.ts'
import DraftEditor from '../components/DraftEditor.tsx'
import type { DraftedEmail, MatchView, SendRequest } from '../api/types.ts'

interface Reasoning {
  strengths?: string[]
  gaps?: string[]
  summary?: string
}

export default function MatchDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [match, setMatch] = useState<MatchView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<DraftedEmail | null>(null)
  const [drafting, setDrafting] = useState(false)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    getMatch(id)
      .then(m => { if (!cancelled) setMatch(m) })
      .catch((e: Error) => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [id])

  const onDraft = async () => {
    if (!id) return
    setDrafting(true)
    setError(null)
    try { setDraft(await draftMatch(id)) }
    catch (e) { setError((e as Error).message) }
    finally { setDrafting(false) }
  }

  const onSend = async ({ subject, body }: SendRequest) => {
    if (!id) return
    setSending(true)
    setError(null)
    try {
      await sendMatch(id, { subject, body })
      navigate('/')
    } catch (e) {
      setError((e as Error).message)
      setSending(false)
    }
  }

  const onSkip = async () => {
    if (!id) return
    if (!confirm('Skip this match?')) return
    try { await skipMatch(id); navigate('/') }
    catch (e) { setError((e as Error).message) }
  }

  if (error) return <div>Error: {error}</div>
  if (!match) return <div>Loading…</div>

  const reasoning: Reasoning | null = match.reasoning ? JSON.parse(match.reasoning) : null

  return (
    <div>
      <h2>{match.posting.title}</h2>
      <div><strong>Company:</strong> {match.posting.company || '—'}</div>
      <div><strong>Location:</strong> {match.posting.location || '—'}</div>
      <div><strong>Contact:</strong> {match.posting.contactEmail || '— (no auto-send possible)'}</div>
      <div><strong>Score:</strong> {match.llmScore ?? '—'} (cosine: {match.cosineSimilarity?.toFixed(2)})</div>
      {reasoning && (
        <div style={{ margin: '1rem 0' }}>
          <strong>Strengths:</strong> {reasoning.strengths?.join(', ') || '—'}<br />
          <strong>Gaps:</strong> {reasoning.gaps?.join(', ') || '—'}<br />
          <em>{reasoning.summary}</em>
        </div>
      )}
      <details>
        <summary>Job description</summary>
        <p>{match.posting.description || '—'}</p>
        <p><strong>Requirements:</strong> {match.posting.requirements || '—'}</p>
      </details>

      {!draft ? (
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
          <button className="button" onClick={onDraft} disabled={drafting || !match.posting.contactEmail}>
            {drafting ? 'Drafting…' : 'Draft cover letter'}
          </button>
          <button className="button secondary" onClick={onSkip}>Skip</button>
        </div>
      ) : (
        <DraftEditor initial={draft} onSend={onSend} onCancel={() => setDraft(null)} sending={sending} />
      )}
    </div>
  )
}
