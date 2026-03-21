import { Link } from "react-router-dom"
import "./ProfileHub.css"

const pageLinks = [
  {
    title: "Anonymous Upload",
    description: "Share safely without exposing your profile identity.",
    to: "/anonymous-upload"
  },
  {
    title: "Anonymous Feed",
    description: "See all approved anonymous posts and interactions.",
    to: "/anonymous-feed"
  },
  {
    title: "Private Live",
    description: "View SOS recorded live videos. These stay private.",
    to: "/camera"
  }
]

const actionButtons = ["Edit Profile", "Settings", "Create"]

export default function ProfileHub() {
  return (
    <div className="profile-hub">
      <div className="profile-hub-card">
        <section className="profile-hub-pages">
          <div className="profile-hub-title">Pages</div>
          <div className="profile-hub-list">
            {pageLinks.map(item => (
              <Link key={item.title} className="profile-hub-page" to={item.to}>
                <div>
                  <div className="profile-hub-page-title">{item.title}</div>
                  <div className="profile-hub-page-desc">{item.description}</div>
                </div>
                <span className="profile-hub-arrow">›</span>
              </Link>
            ))}
          </div>
        </section>
        <section className="profile-hub-actions">
          <div className="profile-hub-title">Quick Actions</div>
          <div className="profile-hub-action-list">
            {actionButtons.map(label => (
              <button key={label} className="profile-hub-action-btn" type="button">
                {label}
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
