import { useState } from 'react'
import { api } from '../services/api'

type ExportItem = { source: string; destination: string }

export default function ExportPage() {
  const [plan, setPlan] = useState<ExportItem[]>([])
  const [mode, setMode] = useState('')
  const [job, setJob] = useState('')

  const previewExport = async () => {
    const data = await api.exportFolders(false)
    setPlan(data.items || [])
    setMode(data.mode)
  }

  const confirmExport = async () => {
    const data = await api.exportFolders(true)
    setPlan(data.items || [])
    setMode(data.mode)
  }

  const startBackgroundExport = async (commit: boolean) => {
    const accepted = await api.queueExport(commit)
    setJob(accepted.job_id)
    setMode('queued')
  }

  const checkJob = async () => {
    if (!job) return
    const status = await api.jobStatus(job)
    setMode(status.status)
    if (status.result?.items) setPlan(status.result.items)
  }

  return (
    <section className="card space-y-3">
      <h2 className="text-lg font-semibold">Export Folders</h2>
      <p className="text-sm text-slate-300">Preview your folder moves before committing files.</p>
      <div className="flex flex-wrap gap-2">
        <button className="rounded-xl bg-slate-700 px-4 py-2" onClick={previewExport}>Dry-run preview</button>
        <button className="rounded-xl bg-violet-500 px-4 py-2" onClick={confirmExport}>Confirm export</button>
        <button className="rounded-xl bg-indigo-600 px-4 py-2" onClick={() => startBackgroundExport(false)}>Queue dry-run job</button>
        <button className="rounded-xl bg-indigo-700 px-4 py-2" onClick={() => startBackgroundExport(true)}>Queue commit job</button>
        <button className="rounded-xl bg-slate-600 px-4 py-2" onClick={checkJob}>Check job</button>
      </div>
      {job && <p className="text-xs text-slate-300">Job: {job}</p>}
      {mode && <p className="text-xs text-violet-300">Mode/Status: {mode}</p>}
      <ul className="text-xs space-y-1 max-h-64 overflow-auto">
        {plan.map((item, index) => (
          <li key={index} className="rounded bg-slate-900/70 p-2">
            <div className="text-slate-400">{item.source}</div>
            <div>→ {item.destination}</div>
          </li>
        ))}
      </ul>
    </section>
  )
}
