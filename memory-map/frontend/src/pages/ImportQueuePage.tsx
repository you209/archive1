import { useEffect, useState } from 'react'
import { api, base } from '../services/api'
import type { Photo } from '../types'

export default function ImportQueuePage() {
  const [queue, setQueue] = useState<Photo[]>([])

  useEffect(() => {
    api.importQueue().then(setQueue)
  }, [])

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">Import Queue</h2>
      <p className="text-sm text-slate-300">AI sorted photos waiting for your review against people profiles and timeline hints.</p>
      <div className="grid md:grid-cols-2 gap-3">
        {queue.map(photo => (
          <article key={photo.id} className="rounded-xl border border-slate-700 p-3 space-y-2">
            <img src={`${base}${photo.media_url}`} alt={photo.original_name} className="w-full h-44 object-cover rounded" />
            <div className="text-sm font-medium">{photo.original_name}</div>
            <div className="space-y-2 text-xs">
              {photo.suggestions.map((s, idx) => (
                <div key={idx} className="rounded-md bg-violet-900/30 px-2 py-2 border border-violet-500/40">
                  <div>{s.kind}: {s.value} ({Math.round(s.confidence * 100)}%)</div>
                  <div className="text-slate-300">Why: {s.reason || 'No reason provided'}</div>
                </div>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
