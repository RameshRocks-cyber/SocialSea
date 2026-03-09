import { useEffect, useMemo, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import {
  FiChevronDown,
  FiImage,
  FiRotateCcw,
  FiSettings,
  FiSlash,
  FiX,
} from "react-icons/fi"
import "./CameraStudio.css"

const FILTERS = [
  { id: "off", label: "Off", badge: "O", filter: "none", mask: "", thumb: "linear-gradient(135deg, #131313, #282828)" },
  { id: "natural", label: "Natural", badge: "N", filter: "none", mask: "", thumb: "linear-gradient(135deg, #50755f, #9ed8a0)" },
  { id: "colorful", label: "Colorful", badge: "C", filter: "saturate(1.62) contrast(1.18) brightness(1.06)", mask: "", thumb: "linear-gradient(135deg, #19a6ff, #7ce2ff 55%, #5adb88)" },
  { id: "cartoon", label: "Cartoon", badge: "T", filter: "contrast(1.34) saturate(1.34) brightness(1.08)", mask: "", thumb: "linear-gradient(135deg, #6f5eff, #f08dff)" },
  { id: "girl", label: "Girl", badge: "G", filter: "brightness(1.1) saturate(1.16) sepia(0.08) hue-rotate(-10deg)", mask: "", thumb: "linear-gradient(135deg, #ff94ca, #ffa3a3)" },
  { id: "boy", label: "Boy", badge: "B", filter: "contrast(1.12) saturate(0.92) hue-rotate(8deg)", mask: "", thumb: "linear-gradient(135deg, #59a4ff, #7ed0ff)" },
  { id: "aging", label: "Aging", badge: "A", filter: "sepia(0.28) contrast(1.16) grayscale(0.18)", mask: "", thumb: "linear-gradient(135deg, #ae8f75, #d0bda3)" },
  { id: "cat", label: "Cat", badge: "CAT", filter: "none", mask: "\uD83D\uDC31", thumb: "linear-gradient(135deg, #f7ab56, #f9d179)" },
  { id: "dog", label: "Dog", badge: "DOG", filter: "none", mask: "\uD83D\uDC36", thumb: "linear-gradient(135deg, #c48f63, #e5cb9f)" },
]

const TOOLS = ["Aa", "\u221E", "\u2727", "\u2304"]

export default function CameraStudio() {
  const navigate = useNavigate()
  const videoRef = useRef(null)
  const streamRef = useRef(null)
  const [error, setError] = useState("")
  const [activeFilterId, setActiveFilterId] = useState("colorful")

  const activeFilter = useMemo(
    () => FILTERS.find((f) => f.id === activeFilterId) || FILTERS[0],
    [activeFilterId]
  )

  useEffect(() => {
    let mounted = true
    const boot = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "user", width: { ideal: 1080 }, height: { ideal: 1920 } },
          audio: false,
        })
        if (!mounted) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        streamRef.current = stream
        if (videoRef.current) {
          videoRef.current.srcObject = stream
          await videoRef.current.play()
        }
      } catch (e) {
        setError(e?.message || "Camera permission denied.")
      }
    }
    void boot()
    return () => {
      mounted = false
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop())
      }
      streamRef.current = null
    }
  }, [])

  return (
    <main className="snapcam-page">
      <section className="snapcam-phone">
        <div className="snapcam-preview">
          <video
            ref={videoRef}
            className="snapcam-video"
            style={{ filter: activeFilter?.filter || "none" }}
            autoPlay
            muted
            playsInline
          />

          <div className="snapcam-topbar">
            <button type="button" className="snapcam-icon-btn" onClick={() => navigate(-1)} title="Close">
              <FiX />
            </button>
            <button type="button" className="snapcam-icon-btn" title="Flash">
              <FiSlash />
            </button>
            <button type="button" className="snapcam-icon-btn" title="Settings">
              <FiSettings />
            </button>
          </div>

          <div className="snapcam-tools">
            {TOOLS.map((tool) => (
              <button key={tool} type="button" className="snapcam-tool-btn">
                {tool}
              </button>
            ))}
          </div>

          {!!activeFilter?.mask && <div className="snapcam-mask">{activeFilter.mask}</div>}

          <div className="snapcam-bottom">
            <div className="snapcam-lenses">
              {FILTERS.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  className={`snapcam-lens ${activeFilterId === f.id ? "is-active" : ""}`}
                  style={{ "--lens-bg": f.thumb }}
                  onClick={() => setActiveFilterId(f.id)}
                  title={f.label}
                >
                  <span>{f.badge}</span>
                </button>
              ))}
            </div>

            <div className="snapcam-control-row">
              <button type="button" className="snapcam-side-btn" title="Gallery">
                <FiImage />
              </button>
              <button type="button" className="snapcam-shutter" title="Capture" />
              <button type="button" className="snapcam-side-btn" title="Rotate">
                <FiRotateCcw />
              </button>
            </div>

            <div className="snapcam-filter-pill">
              <span className="snapcam-pill-mark">\uD83D\uDD16</span>
              <span className="snapcam-pill-label">{activeFilter.label}</span>
              <button type="button" className="snapcam-pill-close" title="Hide">
                <FiChevronDown />
              </button>
            </div>
          </div>

          {error && <div className="snapcam-error">{error}</div>}
        </div>
      </section>
    </main>
  )
}
