import { useEffect, useMemo, useState } from 'react'
import { api, base } from '../services/api'
import type { Photo } from '../types'

export default function ReviewPage() {
  const [queue, setQueue] = useState<Photo[]>([])
  const [who, setWho] = useState('')
  const [year, setYear] = useState('')
  const [event, setEvent] = useState('')
  const [place, setPlace] = useState('')
  const [selected, setSelected] = useState<number[]>([])

  useEffect(() => {
    api.reviewQueue().then(setQueue)
  }, [])

  const current = queue[0]
  const suggestionLookup = useMemo(() => {
    const result: Record<string, { confidence: number; reason?: string }> = {}
    current?.suggestions.forEach(s => {
      result[`${s.kind}:${s.value}`] = { confidence: s.confidence, reason: s.reason }
    })
    return result
  }, [current])

  const submit = async () => {
    if (!current) return
    await api.answer({
      photo_id: current.id,
      who: who.split(',').map(p => p.trim()).filter(Boolean),
      year: year || current.suggested_year,
      event: event || current.suggested_event,
      place,
      notes: 'Reviewed in app'
    })
    setQueue(prev => prev.slice(1))
    setSelected(prev => prev.filter(id => id !== current.id))
    setWho('')
    setYear('')
    setEvent('')
    setPlace('')
  }

  const submitBatch = async () => {
    if (!selected.length) return
    await api.batchAnswer({
      photo_ids: selected,
      who: who.split(',').map(p => p.trim()).filter(Boolean),
      year: year || null,
      event: event || null,
      place: place || null,
      notes: 'Batch reviewed in app'
    })
    setQueue(prev => prev.filter(photo => !selected.includes(photo.id)))
    setSelected([])
    setWho('')
    setYear('')
    setEvent('')
    setPlace('')
  }

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!current) return
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault()
        void submit()
      }
      if (event.key.toLowerCase() === 's') {
        event.preventDefault()
        void submit()
      }
      if (event.key.toLowerCase() === 'b') {
        event.preventDefault()
        void submitBatch()
      }
      if (event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setQueue(prev => prev.slice(1))
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [current, who, year, event, place, selected])

  if (!current) return <section className="card">No photos need review right now.</section>

  const yearMeta = suggestionLookup[`year:${current.suggested_year ?? ''}`]
  const eventMeta = suggestionLookup[`event:${current.suggested_event ?? ''}`]

  return (
    <section className="card space-y-3">
      <h2 className="text-lg font-semibold">Review Queue</h2>
      <p className="text-sm text-slate-300">Shortcut: S or Ctrl+Enter save, B batch-save selected, K skip.</p>
      <div className="rounded-xl border border-slate-700 p-3 text-sm">
        <div className="mb-2 font-medium">Batch review selection</div>
        <div className="grid gap-2 md:grid-cols-2">
          {queue.slice(0, 8).map(photo => (
            <label key={photo.id} className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={selected.includes(photo.id)}
                onChange={e => setSelected(prev => e.target.checked ? [...prev, photo.id] : prev.filter(id => id !== photo.id))}
              />
              <span>{photo.original_name}</span>
            </label>
          ))}
        </div>
        <button className="mt-3 rounded-xl bg-violet-600 px-3 py-2 text-sm" onClick={submitBatch}>Apply current fields to selected</button>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2">
          <div className="rounded-xl bg-slate-800/60 p-3 text-sm">Photo: {current.original_name}</div>
          <img
            src={`${base}${current.media_url}`}
            alt={current.original_name}
            className="w-full rounded-xl border border-slate-700 max-h-[420px] object-contain bg-slate-950"
          />
        </div>
        <div className="space-y-3">
          <label className="block text-sm">Who is in this photo?<input className="w-full rounded bg-slate-800 mt-1 p-2" value={who} onChange={e => setWho(e.target.value)} placeholder="Comma-separated names" /></label>
          <label className="block text-sm">What year is this?
            <input className="w-full rounded bg-slate-800 mt-1 p-2" value={year} onChange={e => setYear(e.target.value)} placeholder={current.suggested_year} />
            <span className="text-xs text-violet-300">Suggested: {current.suggested_year || 'Unknown'} ({Math.round((yearMeta?.confidence || 0) * 100)}%)</span>
            <div className="text-xs text-slate-400">Why: {yearMeta?.reason || 'No provenance details available.'}</div>
          </label>
          <label className="block text-sm">What event is this?
            <input className="w-full rounded bg-slate-800 mt-1 p-2" value={event} onChange={e => setEvent(e.target.value)} placeholder={current.suggested_event} />
            <span className="text-xs text-violet-300">Suggested: {current.suggested_event || 'Unknown'} ({Math.round((eventMeta?.confidence || 0) * 100)}%)</span>
            <div className="text-xs text-slate-400">Why: {eventMeta?.reason || 'No provenance details available.'}</div>
          </label>
          <label className="block text-sm">Where was this taken?<input className="w-full rounded bg-slate-800 mt-1 p-2" value={place} onChange={e => setPlace(e.target.value)} placeholder="City, home, school..." /></label>
          <button className="rounded-xl bg-emerald-500 px-4 py-2" onClick={submit}>Confirm & Learn</button>
        </div>
      </div>
    </section>
  )
}
