import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getMatch, draftMatch, sendMatch, skipMatch } from '../api/client.js'
import DraftEditor from '../components/DraftEditor.jsx'

export default function MatchDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [match, setMatch] = useState(null)
  const [error, setError] = useState(null)
  const [draft, setDraft] = useState(null)
  const [drafting, setDrafting] = useState(false)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    let cancelled = false
    getMatch(id)
      .then(m => { if (!cancelled) setMatch(m) })
      .catch(e => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [id])

  const onDraft = async () => {
    setDrafting(true)
    setError(null)
    try { setDraft(await draftMatch(id)) }
    catch (e) { setError(e.message) }
    finally { setDrafting(false) }
  }

  const onSend = async ({ subject, body }) => {
    setSending(true)
    setError(null)
    try {
      await sendMatch(id, { subject, body })
      navigate('/')
    } catch (e) {
      setError(e.message)
      setSending(false)
    }
  }

  const onSkip = async () => {
    if (!confirm('Skip this match?')) return
    try { await skipMatch(id); navigate('/') }
    catch (e) { setError(e.message) }
  }

  if (error) return <div>Error: {error}</div>
  if (!match) return <div>Loading…</div>

  const reasoning = match.reasoning ? JSON.parse(match.reasoning) : null

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
