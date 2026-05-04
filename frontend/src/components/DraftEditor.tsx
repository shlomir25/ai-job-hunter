import { useState } from 'react'
import type { DraftedEmail, SendRequest } from '../api/types.ts'

interface Props {
  initial: DraftedEmail
  onSend: (payload: SendRequest) => void
  onCancel: () => void
  sending: boolean
}

export default function DraftEditor({ initial, onSend, onCancel, sending }: Props) {
  const [subject, setSubject] = useState(initial.subject)
  const [body, setBody] = useState(initial.body)
  return (
    <div className="draft-editor">
      <label>
        Subject
        <input value={subject} onChange={e => setSubject(e.target.value)} style={{ width: '100%' }} />
      </label>
      <label>
        Body
        <textarea value={body} onChange={e => setBody(e.target.value)} />
      </label>
      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
        <button className="button" disabled={sending} onClick={() => onSend({ subject, body })}>
          {sending ? 'Sending…' : 'Send'}
        </button>
        <button className="button secondary" onClick={onCancel} disabled={sending}>Cancel</button>
      </div>
    </div>
  )
}
