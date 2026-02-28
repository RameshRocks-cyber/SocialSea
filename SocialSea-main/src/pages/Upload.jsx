import { useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch, getToken } from "../services/auth"

export default function Upload() {
  const [file, setFile] = useState(null)
  const [msg, setMsg] = useState("")

  const upload = async () => {
    if (!file) {
      setMsg("Please choose a file first")
      return
    }

    const token = getToken()
    if (!token) {
      setMsg("Login required before uploading")
      return
    }

    const form = new FormData()
    form.append("file", file)

    try {
      const res = await authFetch(`${API_BASE}/api/posts/upload`, {
        method: "POST",
        body: form
      })

      if (res.ok) {
        setMsg("Upload success")
        return
      }

      const errorText = await res.text()
      setMsg(errorText ? `Upload failed: ${errorText}` : "Upload failed")
    } catch (error) {
      setMsg("Upload failed: " + error.message)
    }
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Upload Post</h2>
      <input type="file" onChange={e => setFile(e.target.files[0])} />
      <br /><br />
      <button onClick={upload}>Upload</button>
      <p>{msg}</p>
    </div>
  )
}
