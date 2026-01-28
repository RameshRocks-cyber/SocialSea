import { BrowserRouter, Routes, Route } from "react-router-dom"
import Feed from "./pages/Feed"
import Upload from "./pages/Upload"
import AnonymousUpload from "./pages/AnonymousUpload"
import AnonymousFeed from "./pages/AnonymousFeed"
import Login from "./pages/Login"
import Navbar from "./components/Navbar"
import AdminDashboard from "./admin/AdminDashboard";

function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<Feed />} />
        <Route path="/login" element={<Login />} />
        <Route path="/upload" element={<Upload />} />
        <Route path="/anonymous-upload" element={<AnonymousUpload />} />
        <Route path="/anonymous-feed" element={<AnonymousFeed />} />
        <Route path="/admin/dashboard" element={<AdminDashboard />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
