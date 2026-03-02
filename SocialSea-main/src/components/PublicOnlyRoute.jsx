import { Navigate } from "react-router-dom"
import { isAuthenticated } from "../services/auth"

export default function PublicOnlyRoute({ children }) {
  if (isAuthenticated()) {
    return <Navigate to="/feed" replace />
  }

  return children
}
