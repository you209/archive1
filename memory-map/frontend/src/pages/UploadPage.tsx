import { useState } from 'react'
import { api } from '../services/api'

export default function UploadPage() {
  const [files, setFiles] = useState<File[]>([])
  const [importFolder, setImportFolder] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const onUpload = async () => {
    if (!files.length) return
    setError('')
    try {
      const result = await api.upload(files)
      setMessage(`Uploaded ${result.count} photo(s) for review.`)
    } catch (uploadError) {
      console.error(uploadError)
      setError('Upload failed. Please make sure backend is running and try again.')
    }
  }

  const onImportFolder = async () => {
    if (!importFolder.trim()) return
    const result = await api.importFolder(importFolder)
    setMessage(`Imported ${result.imported} photo(s) from folder dump.`)
  }

  return (
    <section className="card space-y-4">
      <h2 className="text-lg font-semibold">Upload Scanned Photos</h2>
      <p className="text-sm text-slate-300">Bulk upload scans or dump a whole folder for AI-assisted sorting and review assignment.</p>
      <input type="file" multiple accept="image/*" onChange={e => setFiles(Array.from(e.target.files ?? []))} />
      <button className="rounded-xl bg-violet-500 px-4 py-2" onClick={onUpload}>Upload</button>

      <div className="border-t border-slate-700 pt-4 space-y-2">
        <label className="block text-sm">Import folder path on server/local machine</label>
        <input className="w-full rounded bg-slate-800 p-2 text-sm" value={importFolder} onChange={e => setImportFolder(e.target.value)} placeholder="/path/to/dump-folder" />
        <button className="rounded-xl bg-indigo-600 px-4 py-2" onClick={onImportFolder}>Import Folder Dump</button>
      </div>

      {message && <p className="text-emerald-300">{message}</p>}
      {error && <p className="text-rose-300">{error}</p>}
    </section>
  )
}
