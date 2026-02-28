import { BrowserRouter, Routes, Route } from "react-router-dom"
import Feed from "./pages/Feed"
import Upload from "./pages/Upload"
import AnonymousUpload from "./pages/AnonymousUpload"
import AnonymousFeed from "./pages/AnonymousFeed"
import Reels from "./pages/Reels"
import Login from "./pages/Login"
import Navbar from "./components/Navbar"
import AdminDashboard from "./admin/AdminDashboard";
import AdminPendingAnonymous from "./pages/AdminPendingAnonymous"

function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<Feed />} />
        <Route path="/feed" element={<Feed />} />
        <Route path="/home" element={<Feed />} />
        <Route path="/login" element={<Login />} />
        <Route path="/reels" element={<Reels />} />
        <Route path="/upload" element={<Upload />} />
        <Route path="/anonymous-upload" element={<AnonymousUpload />} />
        <Route path="/anonymous/upload" element={<AnonymousUpload />} />
        <Route path="/anonymous-feed" element={<AnonymousFeed />} />
        <Route path="/admin/dashboard" element={<AdminDashboard />} />
        <Route path="/admin/pending" element={<AdminPendingAnonymous />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
