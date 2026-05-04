/** Mirrors backend DTOs in `backend/matching` and `backend/delivery`. */

export type MatchState =
  | 'READY_FOR_REVIEW'
  | 'DRAFTED'
  | 'SENT'
  | 'SKIPPED'
  | 'SEND_FAILED'

export interface PostingView {
  id: number
  title: string | null
  company: string | null
  location: string | null
  isRemote: boolean | null
  language: string | null
  description: string | null
  requirements: string | null
  contactEmail: string | null
  applyUrl: string | null
  sourceUrl: string | null
}

export interface MatchView {
  id: number
  state: MatchState
  llmScore: number | null
  cosineSimilarity: number
  reasoning: string | null
  posting: PostingView
  createdAt: string
}

export interface CvUploadResponse {
  id: number
  label: string
  parsedTextLength: number
  skills: string[]
}

export interface CvListItem {
  id: number
  label: string
  fileName: string
  isActive: boolean
  createdAt: string
}

export interface DraftedEmail {
  subject: string
  body: string
}

export interface SendRequest {
  subject: string
  body: string
}

export type QueueCounts = Record<string, number>
