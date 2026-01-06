import { useEffect, useState } from "react"

export default function AnonymousFeed() {
  const [videos, setVideos] = useState([])

  useEffect(() => {
    fetch("http://localhost:8080/api/anonymous/feed")
      .then(res => res.json())
      .then(data => setVideos(data))
  }, [])

  return (
    <div style={{ padding: 20 }}>
      <h2>Anonymous Videos</h2>

      {videos.map(v => (
        <video
          key={v.id}
          src={`http://localhost:8080${v.videoUrl}`}
          controls
          width="300"
          style={{ marginBottom: 20 }}
        />
      ))}
    </div>
  )
}
