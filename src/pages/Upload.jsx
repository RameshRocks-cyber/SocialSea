import { useState } from "react"

export default function Upload() {
  const [file, setFile] = useState(null)
  const [msg, setMsg] = useState("")

  const upload = async () => {
    const form = new FormData()
    form.append("file", file)

    const res = await fetch("http://localhost:8080/api/posts/upload", {
      method: "POST",
      body: form
    })

    setMsg(res.ok ? "Upload success" : "Upload failed")
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
