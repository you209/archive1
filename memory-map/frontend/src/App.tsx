import { Navigate, Route, Routes } from 'react-router-dom'
import Nav from './components/Nav'
import UploadPage from './pages/UploadPage'
import ReviewPage from './pages/ReviewPage'
import ImportQueuePage from './pages/ImportQueuePage'
import DataPage from './pages/DataPage'
import { api } from './services/api'
import ExportPage from './pages/ExportPage'
import SettingsPage from './pages/SettingsPage'
import PeoplePage from './pages/PeoplePage'
import FamilyTreePage from './pages/FamilyTreePage'
import PhotosPage from './pages/PhotosPage'
import PersonDetailPage from './pages/PersonDetailPage'

export default function App() {
  return (
    <div className="mx-auto max-w-6xl p-4 space-y-4">
      <Nav />
      <Routes>
        <Route path="/" element={<Navigate to="/upload" replace />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/review" element={<ReviewPage />} />
        <Route path="/import-queue" element={<ImportQueuePage />} />
        <Route path="/photos" element={<PhotosPage />} />
        <Route path="/timeline" element={<DataPage title="Timeline" endpoint={api.timeline} columns={['year', 'count']} />} />
        <Route path="/people" element={<PeoplePage />} />
        <Route path="/people/:personId" element={<PersonDetailPage />} />
        <Route path="/family-tree" element={<FamilyTreePage />} />
        <Route path="/events" element={<DataPage title="Events" endpoint={api.events} columns={['event', 'count']} />} />
        <Route path="/map" element={<DataPage title="Map" endpoint={api.map} columns={['place', 'count']} />} />
        <Route path="/export-folders" element={<ExportPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
    </div>
  )
}
