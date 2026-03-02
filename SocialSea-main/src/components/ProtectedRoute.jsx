import { Navigate, useLocation } from "react-router-dom"
import { isAdmin, isAuthenticated } from "../services/auth"

export default function ProtectedRoute({ children, adminOnly = false }) {
  const location = useLocation()

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (adminOnly && !isAdmin()) {
    return <Navigate to="/feed" replace />
  }

  return children
}
