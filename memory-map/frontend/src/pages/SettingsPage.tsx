import { useEffect, useState } from 'react'
import { api } from '../services/api'

export default function SettingsPage() {
  const [reviewConfidence, setReviewConfidence] = useState(0)
  const [importConfidence, setImportConfidence] = useState(0.5)
  const [status, setStatus] = useState('')

  useEffect(() => {
    api.confidenceSettings().then((settings) => {
      setReviewConfidence(settings.min_confidence_review ?? 0)
      setImportConfidence(settings.min_confidence_import_queue ?? 0.5)
    })
  }, [])

  const save = async () => {
    await api.saveConfidenceSettings({
      min_confidence_review: reviewConfidence,
      min_confidence_import_queue: importConfidence
    })
    setStatus('Confidence settings saved.')
  }

  return (
    <section className="card space-y-3">
      <h2 className="text-lg font-semibold">Settings</h2>
      <p className="text-sm text-slate-300">Control which AI suggestions are shown first in review queues.</p>
      <label className="block text-sm">
        Minimum confidence for Review queue ({Math.round(reviewConfidence * 100)}%)
        <input type="range" min={0} max={1} step={0.05} value={reviewConfidence} onChange={e => setReviewConfidence(Number(e.target.value))} className="w-full" />
      </label>
      <label className="block text-sm">
        Minimum confidence for Import Queue ({Math.round(importConfidence * 100)}%)
        <input type="range" min={0} max={1} step={0.05} value={importConfidence} onChange={e => setImportConfidence(Number(e.target.value))} className="w-full" />
      </label>
      <button className="rounded bg-violet-600 px-3 py-2" onClick={save}>Save confidence thresholds</button>
      {status && <p className="text-xs text-emerald-300">{status}</p>}
      <ul className="text-sm list-disc pl-5">
        <li>Photos stay on local storage by default.</li>
        <li>AI suggestions include confidence and provenance notes.</li>
        <li>Import-folder watcher can be enabled via backend settings.</li>
      </ul>
    </section>
  )
}
