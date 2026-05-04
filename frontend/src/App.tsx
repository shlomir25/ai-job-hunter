import { Link, Route, Routes } from 'react-router-dom'
import ReviewQueue from './pages/ReviewQueue.tsx'
import MatchDetail from './pages/MatchDetail.tsx'
import CvUpload from './pages/CvUpload.tsx'
import Dashboard from './pages/Dashboard.tsx'

export default function App() {
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/">Review queue</Link>
        <Link to="/cv">CV</Link>
        <Link to="/dashboard">Dashboard</Link>
      </nav>
      <Routes>
        <Route path="/" element={<ReviewQueue />} />
        <Route path="/matches/:id" element={<MatchDetail />} />
        <Route path="/cv" element={<CvUpload />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </div>
  )
}
