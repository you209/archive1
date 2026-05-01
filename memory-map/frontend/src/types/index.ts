export type Person = { id: number; name: string; confidence: number }

export type Suggestion = { kind: string; value: string; confidence: number; reason?: string }

export type Photo = {
  id: number
  filename: string
  media_url: string
  original_name: string
  suggested_year?: string
  confirmed_year?: string
  suggested_event?: string
  confirmed_event?: string
  suggested_place?: string
  confirmed_place?: string
  notes?: string
  needs_review: boolean
  people: Person[]
  suggestions: Suggestion[]
}
