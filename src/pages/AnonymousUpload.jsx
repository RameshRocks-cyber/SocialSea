import { useState } from "react"

export default function AnonymousUpload() {
  const [file, setFile] = useState(null)
  const [msg, setMsg] = useState("")
  const [title, setTitle] = useState("")

  const upload = async () => {
    try {
      const form = new FormData()
      form.append("file", file)
      form.append("description", title)

      const res = await fetch("http://localhost:8080/api/anonymous/upload", {
        method: "POST",
        body: form
      })

      setMsg(res.ok ? "Anonymous upload success" : "Upload failed")
    } catch (error) {
      setMsg("Upload failed ❌ " + error.message)
    }
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Anonymous Upload</h2>
      <input type="file" onChange={e => setFile(e.target.files[0])} />
      <br /><br />
      <input type="text" placeholder="Enter title" value={title} onChange={e => setTitle(e.target.value)} />
      <br /><br />
      <button onClick={upload}>Upload Anonymously</button>
      <p>{msg}</p>
    </div>
  )
}
