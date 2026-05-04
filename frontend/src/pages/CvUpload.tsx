import { useEffect, useState } from 'react'
import { listCvs, uploadCv } from '../api/client.ts'
import type { CvListItem } from '../api/types.ts'

export default function CvUpload() {
  const [cvs, setCvs] = useState<CvListItem[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [label, setLabel] = useState('default')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = async () => setCvs(await listCvs())

  useEffect(() => { refresh().catch((e: Error) => setError(e.message)) }, [])

  const onUpload = async () => {
    if (!file) return
    setBusy(true)
    setError(null)
    try { await uploadCv(file, label); setFile(null); await refresh() }
    catch (e) { setError((e as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <h2>CV</h2>
      <div>
        <label>
          File
          <input
            type="file"
            accept=".pdf,.docx"
            onChange={e => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <label style={{ marginLeft: '1rem' }}>
          Label
          <input value={label} onChange={e => setLabel(e.target.value)} />
        </label>
        <button className="button" onClick={onUpload} disabled={busy || !file}>
          {busy ? 'Uploading…' : 'Upload'}
        </button>
      </div>
      {error && <div style={{ color: 'red', marginTop: '0.5rem' }}>{error}</div>}
      <h3>Existing CVs</h3>
      <ul>
        {cvs.map(c => (
          <li key={c.id}>
            {c.fileName} <em>({c.label})</em> {c.isActive && <strong>· active</strong>}
          </li>
        ))}
      </ul>
    </div>
  )
}
