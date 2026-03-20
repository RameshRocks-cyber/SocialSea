import { useEffect, useMemo, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"
import "./Chat.css"

const LONG_PRESS_MS = 600

const resolveUrl = url => {
  if (!url) return ""
  if (url.startsWith("http")) return url
  const base = API_BASE || ""
  const needsSlash = !url.startsWith("/") && base
  return `${base}${needsSlash ? "/" : ""}${url}`
}

const getConversationUserId = item => item?.userId ?? item?.id

const getDisplayName = item => {
  if (!item) return "User"
  const name = String(item?.name || "").trim()
  if (name) return name
  const email = String(item?.email || "").trim()
  if (email) return email
  return "User"
}

const getInitials = name => {
  const value = String(name || "").trim()
  if (!value) return "U"
  const parts = value.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
}

const formatTime = value => {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
}

export default function Chat() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [conversations, setConversations] = useState([])
  const [conversationsLoading, setConversationsLoading] = useState(true)
  const [conversationsError, setConversationsError] = useState("")

  const [messages, setMessages] = useState([])
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [messagesError, setMessagesError] = useState("")

  const [text, setText] = useState("")
  const [sending, setSending] = useState(false)

  const [activeUserId, setActiveUserId] = useState(null)
  const [longPressId, setLongPressId] = useState(null)

  const longPressTimerRef = useRef(null)
  const longPressTriggeredRef = useRef(false)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    setActiveUserId(id ? String(id) : null)
  }, [id])

  useEffect(() => {
    loadConversations()
  }, [])

  useEffect(() => {
    if (!activeUserId) {
      setMessages([])
      setMessagesError("")
      return
    }
    loadMessages(activeUserId)
  }, [activeUserId])

  useEffect(() => {
    if (!messagesEndRef.current) return
    messagesEndRef.current.scrollIntoView({ behavior: "smooth", block: "end" })
  }, [messages])

  useEffect(() => {
    return () => {
      if (longPressTimerRef.current) {
        clearTimeout(longPressTimerRef.current)
        longPressTimerRef.current = null
      }
    }
  }, [])

  const activeConversation = useMemo(() => {
    if (!activeUserId) return null
    return (
      conversations.find(item => String(getConversationUserId(item)) === String(activeUserId)) ||
      null
    )
  }, [activeUserId, conversations])

  const loadConversations = async () => {
    setConversationsLoading(true)
    setConversationsError("")
    try {
      const res = await authFetch(`${API_BASE}/api/chat/conversations`)
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `Request failed (${res.status})`)
      }
      const data = await res.json()
      setConversations(Array.isArray(data) ? data : [])
    } catch (err) {
      setConversationsError(err.message || "Failed to load conversations")
    } finally {
      setConversationsLoading(false)
    }
  }

  const loadMessages = async userId => {
    if (!userId) return
    setMessagesLoading(true)
    setMessagesError("")
    try {
      const res = await authFetch(`${API_BASE}/api/chat/${userId}/messages`)
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `Request failed (${res.status})`)
      }
      const data = await res.json()
      const list = Array.isArray(data) ? data : []
      setMessages(
        list.map(item => ({
          id: item?.id ?? `${item?.createdAt || ""}-${Math.random()}`,
          text: item?.text || "",
          createdAt: item?.createdAt,
          mine: Boolean(item?.mine),
          mediaUrl: item?.mediaUrl || "",
          audioUrl: item?.audioUrl || "",
          mediaType: item?.mediaType || "",
          fileName: item?.fileName || ""
        }))
      )
    } catch (err) {
      setMessagesError(err.message || "Failed to load messages")
    } finally {
      setMessagesLoading(false)
    }
  }

  const clearLongPressTimer = () => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current)
      longPressTimerRef.current = null
    }
  }

  const startLongPress = userId => event => {
    if (!userId) return
    if (event.pointerType === "mouse" && event.button !== 0) return
    longPressTriggeredRef.current = false
    clearLongPressTimer()
    longPressTimerRef.current = window.setTimeout(() => {
      longPressTriggeredRef.current = true
      setLongPressId(userId)
    }, LONG_PRESS_MS)
  }

  const stopLongPress = () => {
    clearLongPressTimer()
  }

  const handleItemClick = userId => {
    if (!userId) return
    if (longPressTriggeredRef.current) {
      longPressTriggeredRef.current = false
      return
    }
    if (longPressId && String(longPressId) !== String(userId)) {
      setLongPressId(null)
    }
    navigate(`/chat/${userId}`)
  }

  const handleDelete = async userId => {
    if (!userId) return
    const confirmed = window.confirm("Delete this chat? This will remove the conversation from your account.")
    if (!confirmed) return
    try {
      const res = await authFetch(`${API_BASE}/api/chat/${userId}`, { method: "DELETE" })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `Delete failed (${res.status})`)
      }
      setConversations(prev =>
        prev.filter(item => String(getConversationUserId(item)) !== String(userId))
      )
      if (String(activeUserId) === String(userId)) {
        setMessages([])
        setActiveUserId(null)
        navigate("/chat")
      }
      setLongPressId(null)
    } catch (err) {
      setConversationsError(err.message || "Failed to delete chat")
    }
  }

  const updateConversationPreview = (userId, payload) => {
    if (!userId) return
    const lastMessage = payload?.text || ""
    const lastAt = payload?.createdAt || new Date().toISOString()

    setConversations(prev => {
      const targetId = String(userId)
      const updated = prev.map(item =>
        String(getConversationUserId(item)) === targetId
          ? { ...item, lastMessage, lastAt }
          : item
      )
      const match = updated.find(item => String(getConversationUserId(item)) === targetId)
      if (!match) return updated
      const rest = updated.filter(item => String(getConversationUserId(item)) !== targetId)
      return [match, ...rest]
    })
  }

  const sendMessage = async () => {
    const value = text.trim()
    if (!value || !activeUserId) return
    setSending(true)
    try {
      const res = await authFetch(`${API_BASE}/api/chat/${activeUserId}/send`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: value })
      })
      if (!res.ok) {
        const textRes = await res.text()
        throw new Error(textRes || `Send failed (${res.status})`)
      }
      const data = await res.json()
      const message = {
        id: data?.id ?? `${Date.now()}`,
        text: data?.text || value,
        createdAt: data?.createdAt || new Date().toISOString(),
        mine: true,
        mediaUrl: data?.mediaUrl || "",
        audioUrl: data?.audioUrl || "",
        mediaType: data?.mediaType || "",
        fileName: data?.fileName || ""
      }
      setMessages(prev => [...prev, message])
      updateConversationPreview(activeUserId, message)
      setText("")
    } catch (err) {
      setMessagesError(err.message || "Failed to send message")
    } finally {
      setSending(false)
    }
  }

  const showThread = Boolean(activeUserId)

  return (
    <div className="chat-page">
      <div className="chat-shell">
        <section className={`chat-list ${showThread ? "is-hidden-mobile" : ""}`}>
          <div className="chat-list-header">
            <span className="chat-list-title">Messages</span>
            <button type="button" className="chat-secondary-btn" onClick={loadConversations}>
              Refresh
            </button>
          </div>

          {conversationsError && <div className="chat-alert">{conversationsError}</div>}

          <div className="chat-list-body">
            {conversationsLoading && <div className="chat-empty">Loading chats...</div>}
            {!conversationsLoading && conversations.length === 0 && (
              <div className="chat-empty">No chats yet.</div>
            )}

            {conversations.map(item => {
              const userId = getConversationUserId(item)
              const name = getDisplayName(item)
              const lastMessage = item?.lastMessage || "No messages yet"
              const lastAt = item?.lastAt
              const isActive = String(userId) === String(activeUserId)

              return (
                <div
                  key={String(userId)}
                  className={`chat-item ${isActive ? "active" : ""}`}
                  onClick={() => handleItemClick(userId)}
                  onPointerDown={startLongPress(userId)}
                  onPointerUp={stopLongPress}
                  onPointerLeave={stopLongPress}
                  onPointerCancel={stopLongPress}
                  onContextMenu={event => {
                    event.preventDefault()
                    setLongPressId(userId)
                  }}
                >
                  <div className="chat-avatar">
                    {item?.profilePic ? (
                      <img src={resolveUrl(item.profilePic)} alt={name} />
                    ) : (
                      <span>{getInitials(name)}</span>
                    )}
                  </div>
                  <div className="chat-item-main">
                    <div className="chat-item-row">
                      <span className="chat-item-name">{name}</span>
                      <span className="chat-item-time">{formatTime(lastAt)}</span>
                    </div>
                    <div className="chat-item-preview">{lastMessage}</div>

                    {String(longPressId) === String(userId) && (
                      <div className="chat-item-actions">
                        <button
                          type="button"
                          className="chat-delete-btn"
                          onClick={event => {
                            event.stopPropagation()
                            handleDelete(userId)
                          }}
                        >
                          Delete chat
                        </button>
                        <button
                          type="button"
                          className="chat-cancel-btn"
                          onClick={event => {
                            event.stopPropagation()
                            setLongPressId(null)
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </section>

        <section className={`chat-thread ${!showThread ? "is-hidden-mobile" : ""}`}>
          <div className="chat-thread-header">
            <button type="button" className="chat-back" onClick={() => navigate("/chat")}>
              Back
            </button>
            <div className="chat-thread-title">
              {activeConversation ? getDisplayName(activeConversation) : "Conversation"}
            </div>
          </div>

          {messagesError && <div className="chat-alert">{messagesError}</div>}

          <div className="chat-thread-body">
            {!activeUserId && <div className="chat-empty">Select a chat to start messaging.</div>}
            {activeUserId && messagesLoading && <div className="chat-empty">Loading messages...</div>}
            {activeUserId && !messagesLoading && messages.length === 0 && (
              <div className="chat-empty">No messages yet.</div>
            )}

            {messages.map(message => (
              <div
                key={String(message.id)}
                className={`chat-message ${message.mine ? "is-mine" : ""}`}
              >
                <div className="chat-bubble">
                  {message.text || (message.mediaUrl ? "Media message" : "Message")}
                </div>
                <span className="chat-time">{formatTime(message.createdAt)}</span>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          <div className="chat-input-row">
            <input
              type="text"
              value={text}
              onChange={event => setText(event.target.value)}
              placeholder={activeUserId ? "Type a message..." : "Select a chat first"}
              onKeyDown={event => event.key === "Enter" && sendMessage()}
              disabled={!activeUserId || sending}
            />
            <button type="button" onClick={sendMessage} disabled={!activeUserId || sending}>
              {sending ? "Sending..." : "Send"}
            </button>
          </div>
        </section>
      </div>
    </div>
  )
}
