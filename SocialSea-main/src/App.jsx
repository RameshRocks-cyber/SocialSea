import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"
import Feed from "./pages/Feed"
import Upload from "./pages/Upload"
import AnonymousUpload from "./pages/AnonymousUpload"
import AnonymousFeed from "./pages/AnonymousFeed"
import Reels from "./pages/Reels"
import CameraStudio from "./pages/CameraStudio"
import ProfileHub from "./pages/ProfileHub"
import Login from "./pages/Login"
import ForgotPassword from "./pages/ForgotPassword"
import Chat from "./pages/Chat"
import Navbar from "./components/Navbar"
import AdminDashboard from "./pages/AdminDashboard"
import AdminPendingAnonymous from "./pages/AdminPendingAnonymous"
import AdminSosNearby from "./pages/AdminSosNearby"
import ProtectedRoute from "./components/ProtectedRoute"
import PublicOnlyRoute from "./components/PublicOnlyRoute"
import { isAuthenticated } from "./services/auth"

function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route
          path="/"
          element={<Navigate to={isAuthenticated() ? "/feed" : "/login"} replace />}
        />
        <Route
          path="/login"
          element={
            <PublicOnlyRoute>
              <Login />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/forgot-password"
          element={
            <PublicOnlyRoute>
              <ForgotPassword />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/feed"
          element={
            <ProtectedRoute>
              <Feed />
            </ProtectedRoute>
          }
        />
        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <Feed />
            </ProtectedRoute>
          }
        />
        <Route
          path="/reels"
          element={
            <ProtectedRoute>
              <Reels />
            </ProtectedRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <ProtectedRoute>
              <ProfileHub />
            </ProtectedRoute>
          }
        />
        <Route
          path="/camera"
          element={
            <ProtectedRoute>
              <CameraStudio />
            </ProtectedRoute>
          }
        />
        <Route
          path="/upload"
          element={
            <ProtectedRoute>
              <Upload />
            </ProtectedRoute>
          }
        />
        <Route
          path="/chat"
          element={
            <ProtectedRoute>
              <Chat />
            </ProtectedRoute>
          }
        />
        <Route
          path="/chat/:id"
          element={
            <ProtectedRoute>
              <Chat />
            </ProtectedRoute>
          }
        />
        <Route
          path="/anonymous-upload"
          element={
            <ProtectedRoute>
              <AnonymousUpload />
            </ProtectedRoute>
          }
        />
        <Route
          path="/anonymous/upload"
          element={
            <ProtectedRoute>
              <AnonymousUpload />
            </ProtectedRoute>
          }
        />
        <Route
          path="/anonymous-feed"
          element={
            <ProtectedRoute>
              <AnonymousFeed />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/dashboard"
          element={
            <ProtectedRoute adminOnly>
              <AdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/pending"
          element={
            <ProtectedRoute adminOnly>
              <AdminPendingAnonymous />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/sos-nearby"
          element={
            <ProtectedRoute adminOnly>
              <AdminSosNearby />
            </ProtectedRoute>
          }
        />
        <Route
          path="*"
          element={<Navigate to={isAuthenticated() ? "/feed" : "/login"} replace />}
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App
