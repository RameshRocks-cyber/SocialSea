import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { API_BASE } from "../services/api"

export default function ForgotPassword() {
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState("")
  const [otp, setOtp] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [msg, setMsg] = useState("")
  const [sending, setSending] = useState(false)
  const [resetting, setResetting] = useState(false)

  const sendOtp = async () => {
    const trimmed = identifier.trim()
    if (!trimmed) {
      setMsg("Please enter your username or email")
      return
    }
    setSending(true)
    try {
      const res = await fetch(`${API_BASE}/api/auth/send-otp`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: trimmed, email: trimmed })
      })
      const payload = await res.json().catch(() => ({}))
      if (res.ok) {
        if (payload?.debugOtp) {
          setMsg(`OTP delivery failed. Use fallback OTP: ${payload.debugOtp}`)
        } else if (payload?.deliveryFailed) {
          setMsg(payload?.message || "OTP generated, but delivery failed")
        } else {
          setMsg(payload?.message || "OTP sent successfully")
        }
      } else {
        const text = payload?.message || payload?.error || "Request failed"
        setMsg("Failed to send OTP: " + text)
      }
    } catch (e) {
      setMsg("Error sending OTP. Is backend running?")
    } finally {
      setSending(false)
    }
  }

  const resetPassword = async () => {
    const trimmed = identifier.trim()
    if (!trimmed || !otp.trim() || !newPassword) {
      setMsg("Please fill all fields")
      return
    }
    if (newPassword !== confirmPassword) {
      setMsg("Passwords do not match")
      return
    }
    setResetting(true)
    try {
      const res = await fetch(`${API_BASE}/api/auth/reset-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: trimmed,
          username: trimmed,
          email: trimmed.includes("@") ? trimmed : undefined,
          otp: otp.trim(),
          newPassword
        })
      })
      const payload = await res.json().catch(() => ({}))
      if (res.ok) {
        setMsg(payload?.message || "Password reset successful")
        setTimeout(() => navigate("/login"), 1200)
      } else {
        const text = payload?.message || payload?.error || "Reset failed"
        setMsg(text)
      }
    } catch (e) {
      setMsg("Error resetting password")
    } finally {
      setResetting(false)
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.box}>
        <h1 style={styles.logo}>SocialSea</h1>
        <h2 style={styles.subtitle}>Enter your username or email to reset your password.</h2>

        <input
          onChange={e => setIdentifier(e.target.value)}
          placeholder="Username or email"
          style={styles.input}
        />

        <button onClick={sendOtp} style={styles.button} disabled={sending}>
          {sending ? "Sending..." : "Send OTP"}
        </button>

        <input
          onChange={e => setOtp(e.target.value)}
          placeholder="Enter OTP"
          style={styles.input}
        />

        <div style={styles.passwordRow}>
          <input
            type={showNewPassword ? "text" : "password"}
            onChange={e => setNewPassword(e.target.value)}
            placeholder="New password"
            style={styles.input}
          />
          <button
            type="button"
            onClick={() => setShowNewPassword(v => !v)}
            style={styles.showBtn}
          >
            {showNewPassword ? "Hide" : "Show"}
          </button>
        </div>

        <div style={styles.passwordRow}>
          <input
            type={showConfirmPassword ? "text" : "password"}
            onChange={e => setConfirmPassword(e.target.value)}
            placeholder="Confirm new password"
            style={styles.input}
          />
          <button
            type="button"
            onClick={() => setShowConfirmPassword(v => !v)}
            style={styles.showBtn}
          >
            {showConfirmPassword ? "Hide" : "Show"}
          </button>
        </div>

        <button onClick={resetPassword} style={styles.button} disabled={resetting}>
          {resetting ? "Resetting..." : "Reset password"}
        </button>

        {msg && <p style={styles.error}>{msg}</p>}
      </div>

      <div style={styles.box}>
        <p style={styles.loginText}>
          Remembered it? <Link to="/login" style={styles.link}>Back to login</Link>
        </p>
      </div>
    </div>
  )
}

const styles = {
  container: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    minHeight: "100vh",
    backgroundColor: "#fafafa",
    color: "#262626",
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    padding: "10px"
  },
  box: {
    border: "1px solid #dbdbdb",
    backgroundColor: "#fff",
    padding: "20px",
    width: "100%",
    maxWidth: "350px",
    marginBottom: "10px",
    textAlign: "center",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    boxSizing: "border-box"
  },
  logo: {
    fontSize: "3rem",
    marginBottom: "20px",
    marginTop: "0",
    fontFamily: "cursive"
  },
  subtitle: {
    fontSize: "15px",
    fontWeight: "600",
    color: "#8e8e8e",
    marginBottom: "20px",
    lineHeight: "20px"
  },
  input: {
    width: "100%",
    padding: "9px 8px",
    marginBottom: "6px",
    backgroundColor: "#fafafa",
    border: "1px solid #dbdbdb",
    borderRadius: "3px",
    color: "#262626",
    fontSize: "12px",
    outline: "none",
    boxSizing: "border-box"
  },
  button: {
    width: "100%",
    backgroundColor: "#0095f6",
    color: "#fff",
    border: "none",
    borderRadius: "4px",
    padding: "7px 16px",
    fontWeight: "600",
    cursor: "pointer",
    marginTop: "10px",
    fontSize: "14px"
  },
  passwordRow: {
    width: "100%",
    display: "flex",
    alignItems: "center",
    gap: "6px"
  },
  showBtn: {
    padding: "7px 10px",
    borderRadius: "4px",
    border: "1px solid #dbdbdb",
    background: "#f0f0f0",
    cursor: "pointer",
    fontSize: "12px",
    height: "34px",
    marginBottom: "6px"
  },
  link: {
    color: "#0095f6",
    textDecoration: "none",
    fontWeight: "600"
  },
  loginText: {
    fontSize: "14px",
    margin: "15px 0"
  },
  error: {
    color: "#ed4956",
    fontSize: "14px",
    marginTop: "10px"
  }
}
