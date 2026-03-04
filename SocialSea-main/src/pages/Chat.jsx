import { useEffect, useMemo, useRef, useState } from "react"
import "./Chat.css"

const INITIAL_MESSAGES = [
  { id: 1, from: "Alex", text: "Hey, can we move this chat window around?" },
  { id: 2, from: "You", text: "Yes, drag the top bar to move it." }
]

export default function Chat() {
  const panelRef = useRef(null)
  const dragRef = useRef({
    active: false,
    offsetX: 0,
    offsetY: 0
  })

  const [text, setText] = useState("")
  const [messages, setMessages] = useState(INITIAL_MESSAGES)
  const [position, setPosition] = useState({ x: 24, y: 90 })

  const boundedPosition = useMemo(() => {
    const panel = panelRef.current
    if (!panel) return position

    const viewportWidth = window.visualViewport?.width || window.innerWidth
    const viewportHeight = window.visualViewport?.height || window.innerHeight
    const maxX = Math.max(0, viewportWidth - panel.offsetWidth - 12)
    const maxY = Math.max(0, viewportHeight - panel.offsetHeight - 12)
    return {
      x: Math.min(Math.max(position.x, 12), maxX),
      y: Math.min(Math.max(position.y, 12), maxY)
    }
  }, [position])

  useEffect(() => {
    const onMove = event => {
      if (!dragRef.current.active) return
      setPosition({
        x: event.clientX - dragRef.current.offsetX,
        y: event.clientY - dragRef.current.offsetY
      })
    }

    const onUp = () => {
      dragRef.current.active = false
    }

    window.addEventListener("pointermove", onMove)
    window.addEventListener("pointerup", onUp)
    return () => {
      window.removeEventListener("pointermove", onMove)
      window.removeEventListener("pointerup", onUp)
    }
  }, [])

  useEffect(() => {
    const onResize = () => setPosition(prev => ({ ...prev }))
    window.addEventListener("resize", onResize)
    return () => window.removeEventListener("resize", onResize)
  }, [])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = "hidden"
    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [])

  const beginDrag = event => {
    const panel = panelRef.current
    if (!panel) return
    dragRef.current.active = true
    dragRef.current.offsetX = event.clientX - panel.offsetLeft
    dragRef.current.offsetY = event.clientY - panel.offsetTop
  }

  const sendMessage = () => {
    const value = text.trim()
    if (!value) return
    setMessages(prev => [...prev, { id: Date.now(), from: "You", text: value }])
    setText("")
  }

  return (
    <div className="chat-page">
      <div
        ref={panelRef}
        className="chat-panel"
        style={{
          left: `${boundedPosition.x}px`,
          top: `${boundedPosition.y}px`
        }}
      >
        <div className="chat-panel-header" onPointerDown={beginDrag} role="button" tabIndex={0}>
          <span>Chat</span>
          <small>Drag me</small>
        </div>

        <div className="chat-messages">
          {messages.map(message => (
            <div
              key={message.id}
              className={`chat-bubble ${message.from === "You" ? "chat-bubble-self" : ""}`}
            >
              <strong>{message.from}:</strong> {message.text}
            </div>
          ))}
        </div>

        <div className="chat-input-row">
          <input
            type="text"
            value={text}
            onChange={e => setText(e.target.value)}
            placeholder="Type a message..."
            onKeyDown={e => e.key === "Enter" && sendMessage()}
          />
          <button type="button" onClick={sendMessage}>
            Send
          </button>
        </div>
      </div>
    </div>
  )
}
