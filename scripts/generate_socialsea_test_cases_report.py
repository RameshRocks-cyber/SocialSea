from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from html import escape
from pathlib import Path


@dataclass(frozen=True)
class TestCase:
    id: str
    scenario: str
    steps: str
    expected: str


@dataclass(frozen=True)
class Section:
    title: str
    note: str
    cases: list[TestCase]


SECTIONS: list[Section] = [
    Section(
        title="Authentication and Session Management",
        note="Covers register, login, token refresh, and rejection paths for unauthenticated access.",
        cases=[
            TestCase(
                id="AUTH-01",
                scenario="Register a new account with valid email, password, and profile basics.",
                steps="Open /register; submit valid details; continue through first-time setup or landing flow.",
                expected="Account is created and the user is authenticated with the correct landing route.",
            ),
            TestCase(
                id="AUTH-02",
                scenario="Login with valid credentials.",
                steps="Open /login; enter a known email and password; submit the form.",
                expected="User is authenticated, session cookies are set, and protected pages become available.",
            ),
            TestCase(
                id="AUTH-03",
                scenario="Login with invalid credentials is rejected.",
                steps="Open /login; enter the wrong password or an unknown email; submit the form.",
                expected="An error is shown and no auth cookies or session state are created.",
            ),
            TestCase(
                id="AUTH-04",
                scenario="Refresh token rotation succeeds and invalid refresh tokens are cleared.",
                steps="Call the refresh endpoint with a valid refresh cookie; repeat with an expired or invalid token.",
                expected="Valid refresh returns a new access token cookie, while invalid refresh clears cookies and returns unauthorized.",
            ),
        ],
    ),
    Section(
        title="Feed Pages, Posts, and Saved Content",
        note="Validates the main feed page, search and filter controls, post viewer actions, long-video mode, and saved-state flows.",
        cases=[
            TestCase(
                id="FEED-01",
                scenario="Open the main feed as an authenticated user.",
                steps="Sign in, navigate to /feed, wait for loading to finish, and scroll the visible timeline.",
                expected="The protected feed route loads posts and media without redirecting the authenticated user away.",
            ),
            TestCase(
                id="FEED-02",
                scenario="Search the feed by caption or username and then clear the search.",
                steps="Type a known caption or username in the search box, confirm the list narrows, and then clear the field.",
                expected="Only matching posts remain while the search is active, and clearing the search restores the full feed.",
            ),
            TestCase(
                id="FEED-03",
                scenario="Switch between Feed and Video modes.",
                steps="Click the Video tab to open the long-form layout, then switch back to the Feed tab.",
                expected="Video mode shows long-form cards and Feed mode returns to the standard timeline layout.",
            ),
            TestCase(
                id="FEED-04",
                scenario="Apply feed category filters and sub-filters.",
                steps="Open the feed options, choose a category such as study or gaming, and pick one of the sub-filters when available.",
                expected="The feed updates to the selected category and sub-filter, and the empty state appears when nothing matches.",
            ),
            TestCase(
                id="FEED-05",
                scenario="Open a post viewer and use the post actions.",
                steps="Select a post tile, like it, add a comment, save it, share it, and close the viewer.",
                expected="The viewer opens, the actions respond, the comment appears, save state toggles, and closing returns to the feed.",
            ),
            TestCase(
                id="FEED-06",
                scenario="Save a feed post and confirm the saved state persists.",
                steps="Save a post from the feed, refresh the page, and open /saved to confirm the post is still stored.",
                expected="The save button stays active after refresh and the item appears in the Saved page until it is removed.",
            ),
        ],
    ),
    Section(
        title="Profiles and Follow Relationships",
        note="Covers public profile viewing, private account rules, and follow request lifecycle.",
        cases=[
            TestCase(
                id="PROFILE-01",
                scenario="View and edit the current user's profile.",
                steps="Open /profile/me; update basic profile fields such as name or avatar; save changes.",
                expected="The profile reflects the updated values and the current user remains on the profile flow.",
            ),
            TestCase(
                id="PROFILE-02",
                scenario="Open another user's public profile.",
                steps="Navigate to /profile/:username for a public account; inspect posts, followers, and following counts.",
                expected="Public profile data is visible without privacy leakage and counts render correctly.",
            ),
            TestCase(
                id="PROFILE-03",
                scenario="Send a follow request to a private account.",
                steps="Open a private profile; tap Follow; refresh the profile or request inbox.",
                expected="The request moves to a pending state and private content remains hidden until accepted.",
            ),
            TestCase(
                id="PROFILE-04",
                scenario="Accept a follow request and verify private content becomes visible.",
                steps="Open the follow requests page; accept the request; revisit the private profile.",
                expected="The requester becomes a follower and private posts or stories become visible to that follower.",
            ),
        ],
    ),
    Section(
        title="Stories, Reels, and Video Playback",
        note="Checks the media-heavy social surfaces, including privacy and player behavior.",
        cases=[
            TestCase(
                id="STORY-01",
                scenario="Create and publish a story.",
                steps="Open /story/create; add media and privacy; publish the story.",
                expected="The story is created successfully and becomes visible in the story surface for eligible viewers.",
            ),
            TestCase(
                id="STORY-02",
                scenario="Private stories stay hidden from non-followers.",
                steps="View a private account as a non-follower; open /stories or the relevant profile story surface.",
                expected="The story does not appear to unauthorized users.",
            ),
            TestCase(
                id="STORY-03",
                scenario="Followers can view stories and view counts update.",
                steps="Open the story as an approved follower; watch it; reload the story feed.",
                expected="The follower can view the story and the view counter increments once per viewer as expected.",
            ),
            TestCase(
                id="STORY-04",
                scenario="Only one video plays at a time in reels or long-video feeds.",
                steps="Open /clips or /watch; start playback on one video, then start another.",
                expected="The previous video pauses so the feed does not play multiple clips simultaneously.",
            ),
        ],
    ),
    Section(
        title="Chat and Realtime Presence",
        note="Validates direct chats, request inboxes, member access, and presence heartbeats.",
        cases=[
            TestCase(
                id="CHAT-01",
                scenario="Open a direct conversation route and load message history.",
                steps="Navigate to /chat/:contactId; wait for the thread to load; scroll through older messages.",
                expected="The conversation history loads and the route remains protected.",
            ),
            TestCase(
                id="CHAT-02",
                scenario="Send a message and receive it in realtime.",
                steps="Open an active conversation; send a message from one client; observe it from the other client.",
                expected="The message appears without a manual refresh and preserves conversation order.",
            ),
            TestCase(
                id="CHAT-03",
                scenario="Open chat requests or a group chat and enforce member-only access.",
                steps="Visit /chat/requests or a group conversation; try access as a non-member.",
                expected="Only the correct participant set can see the conversation or request inbox.",
            ),
            TestCase(
                id="CHAT-04",
                scenario="Presence heartbeat stays active while the user remains on the app.",
                steps="Keep the user online on a non-auth page; switch tabs; return to the app.",
                expected="Presence updates are sent while active and the UI state stays in sync with activity.",
            ),
        ],
    ),
    Section(
        title="Notifications and Activity Tracking",
        note="Covers notification badges, read-state updates, and route-level activity logging.",
        cases=[
            TestCase(
                id="NOTIF-01",
                scenario="Unread notification count appears in the bell.",
                steps="Generate a new notification; return to the app shell; inspect the notification icon.",
                expected="The unread badge or count becomes visible and matches the unread total.",
            ),
            TestCase(
                id="NOTIF-02",
                scenario="Mark a notification as read and confirm the count drops.",
                steps="Open the notifications page; mark one notification read; refresh the page.",
                expected="The read item is removed from the unread count and the change persists after reload.",
            ),
            TestCase(
                id="NOTIF-03",
                scenario="Open notifications and job notifications pages.",
                steps="Navigate to /notifications and /job-notifications.",
                expected="Both pages load successfully for authenticated users.",
            ),
            TestCase(
                id="NOTIF-04",
                scenario="Activity tracking records time spent and external link clicks.",
                steps="Navigate across a few routes; click an external link; return to the app.",
                expected="Activity events are captured without blocking navigation or crashing the shell.",
            ),
        ],
    ),
    Section(
        title="Anonymous Feed and Moderation",
        note="Tests anonymous feed loading, anonymous post interactions, retry behavior, and moderation actions.",
        cases=[
            TestCase(
                id="ANON-01",
                scenario="Open the anonymous feed and verify approved items load.",
                steps="Sign in, navigate to /anonymous-feed, and wait for the anonymous timeline to render.",
                expected="Only approved anonymous posts appear and the page shows the anonymous feed content without private leaks.",
            ),
            TestCase(
                id="ANON-02",
                scenario="Like an anonymous post and confirm the count updates.",
                steps="Open /anonymous-feed, click Like on an item, and refresh the page.",
                expected="The like count increases and the item remains available after a refresh.",
            ),
            TestCase(
                id="ANON-03",
                scenario="Load an anonymous image or video and verify view tracking.",
                steps="Open /anonymous-feed, let the media load, and then reload the page.",
                expected="The view count is recorded once per item and the media preview renders correctly.",
            ),
            TestCase(
                id="ANON-04",
                scenario="Retry an anonymous feed request after a transient failure.",
                steps="Open /anonymous-feed when the endpoint is unavailable, confirm the error state, and click Retry.",
                expected="The error is shown clearly, cached posts remain visible when available, and the feed reloads after Retry.",
            ),
            TestCase(
                id="ANON-05",
                scenario="Approve a pending anonymous item from moderation.",
                steps="Open the admin anonymous pending queue and approve one anonymous post.",
                expected="The item moves out of pending and becomes visible in the approved anonymous feed.",
            ),
            TestCase(
                id="ANON-06",
                scenario="Reject a pending anonymous item from moderation.",
                steps="Open the admin anonymous pending queue, reject one item, and inspect the rejected list.",
                expected="The item is removed from pending and appears in the rejected moderation workflow.",
            ),
        ],
    ),
    Section(
        title="Jobs, Companies, and Resumes",
        note="Covers job browsing, applications, employer tools, and resume-related pages.",
        cases=[
            TestCase(
                id="JOB-01",
                scenario="Browse jobs and open a job detail page.",
                steps="Open /jobs; select a listing; open /jobs/:jobId.",
                expected="The list renders correctly and the job detail page shows the selected opening.",
            ),
            TestCase(
                id="JOB-02",
                scenario="Apply to a job and confirm the application state.",
                steps="Open /jobs/:jobId/apply; submit the application; open applied jobs or notifications.",
                expected="The application is stored and the user can see the applied status afterward.",
            ),
            TestCase(
                id="JOB-03",
                scenario="Post a job and verify it appears in company tools.",
                steps="Open /post-job; create a listing; open /company-hub or /job-profile.",
                expected="The job is created and visible in employer-facing job management pages.",
            ),
            TestCase(
                id="JOB-04",
                scenario="Open applicant inbox, applicant profile, and resume tools.",
                steps="Visit /applicant-inbox and /applicants/:applicationId; open /resume-builder and /resume-templates.",
                expected="Applicant and resume pages load successfully and support the hiring workflow.",
            ),
        ],
    ),
    Section(
        title="Live Streaming and Media Upload",
        note="Validates live start/watch flows, recordings, and media upload handling.",
        cases=[
            TestCase(
                id="LIVE-01",
                scenario="Start a live broadcast.",
                steps="Open /live/start; configure the broadcast; start streaming.",
                expected="The live session begins and the creator is routed into the active live flow.",
            ),
            TestCase(
                id="LIVE-02",
                scenario="Join or watch a live session.",
                steps="Open /live/watch or a live session link; join as a viewer.",
                expected="The viewer can watch the stream and the watch route loads without errors.",
            ),
            TestCase(
                id="LIVE-03",
                scenario="View live recordings and long-video content.",
                steps="Open /live-recordings or /profile/live-recordings; then open /watch.",
                expected="Recorded sessions and long-form video pages render and can be browsed.",
            ),
            TestCase(
                id="LIVE-04",
                scenario="Upload supported media and verify progress and validation.",
                steps="Open /upload; submit supported media types; watch upload progress and completion.",
                expected="Supported files upload successfully and invalid media is rejected with a clear error.",
            ),
        ],
    ),
    Section(
        title="SOS and Emergency Navigation",
        note="Covers emergency alert creation, live alert pages, and navigation routes.",
        cases=[
            TestCase(
                id="SOS-01",
                scenario="Create an SOS alert from the SOS page.",
                steps="Open /sos; trigger a new emergency alert; confirm the alert is created.",
                expected="The alert is created and the user is routed into the live emergency flow.",
            ),
            TestCase(
                id="SOS-02",
                scenario="Open a live alert route and verify the details panel.",
                steps="Navigate to /sos/live/:alertId; inspect the alert details and status.",
                expected="The alert page loads and shows the correct live emergency metadata.",
            ),
            TestCase(
                id="SOS-03",
                scenario="Open SOS navigation and verify the navigation controls.",
                steps="Navigate to /sos/navigate/:alertId; use the map or route controls.",
                expected="The navigation view renders and route guidance remains available to the responder.",
            ),
            TestCase(
                id="SOS-04",
                scenario="Open ambulance navigation and verify geolocation behavior.",
                steps="Open /ambulance; grant or deny location permission; observe the map or route state.",
                expected="The page responds appropriately to geolocation permissions and shows the correct navigation workflow.",
            ),
        ],
    ),
    Section(
        title="Storage Vault and Recordings",
        note="Covers lock/unlock flows and access to stored call or media recordings.",
        cases=[
            TestCase(
                id="VAULT-01",
                scenario="Unlock the storage vault with valid credentials.",
                steps="Open /storage/unlock; enter valid unlock data; submit the form.",
                expected="The vault unlocks and the protected storage pages become accessible.",
            ),
            TestCase(
                id="VAULT-02",
                scenario="Block vault pages before unlocking.",
                steps="Try to open /storage or /storage/call-recordings before unlocking the vault.",
                expected="Access is denied or redirected until the vault is unlocked.",
            ),
            TestCase(
                id="VAULT-03",
                scenario="Open call recordings after unlocking.",
                steps="Unlock the vault first; then open /storage/call-recordings.",
                expected="The call recordings page loads and shows the stored recordings.",
            ),
            TestCase(
                id="VAULT-04",
                scenario="Verify saved media and recently deleted flows do not leak locked content.",
                steps="Move items through the save or delete lifecycle; refresh the vault views; inspect item visibility.",
                expected="Locked content stays hidden and only the intended vault items are shown.",
            ),
        ],
    ),
    Section(
        title="Admin Console",
        note="Covers dashboard metrics, moderation tools, live recording review, and request queues.",
        cases=[
            TestCase(
                id="ADMIN-01",
                scenario="Open the admin dashboard as an admin user.",
                steps="Sign in as ADMIN; navigate to /admin/dashboard.",
                expected="The admin dashboard loads metrics and only admins can reach the page.",
            ),
            TestCase(
                id="ADMIN-02",
                scenario="Manage users and moderation notices.",
                steps="Open /admin/users and the yellow or red moderation notice pages.",
                expected="User management and moderation notice views load and update without errors.",
            ),
            TestCase(
                id="ADMIN-03",
                scenario="Review posts and reports.",
                steps="Open /admin/posts and /admin/reports; resolve one moderation item.",
                expected="The post or report leaves the active queue after the moderation action succeeds.",
            ),
            TestCase(
                id="ADMIN-04",
                scenario="Review anonymous queues and live recordings.",
                steps="Open /admin/anonymous/pending, /admin/anonymous/videos, and /admin/live-recordings.",
                expected="Each moderation queue loads and displays the correct items for admin review.",
            ),
            TestCase(
                id="ADMIN-05",
                scenario="Review ambulance requests and admin notifications.",
                steps="Open /admin/ambulance and /admin/notifications; process one visible item.",
                expected="The request and notification queues load and support admin follow-up actions.",
            ),
        ],
    ),
    Section(
        title="Settings, Routing, and Security",
        note="Covers preference persistence, device sessions, protected routes, and safe cookie behavior.",
        cases=[
            TestCase(
                id="SET-01",
                scenario="Update appearance, language, and content-type preferences.",
                steps="Open /settings/appearance, /settings/language, and /settings/content-types; change options; refresh.",
                expected="The selected preferences persist and the UI reflects the stored settings.",
            ),
            TestCase(
                id="SET-02",
                scenario="Update sounds, location, and notification-buddy settings.",
                steps="Open /settings/sounds, /settings/location, and /settings/notification-buddy; change each option.",
                expected="Settings persist and the corresponding route views load correctly after reload.",
            ),
            TestCase(
                id="SET-03",
                scenario="Manage login activity and device sessions.",
                steps="Open /settings/login-activity; inspect active sessions; remove one session if available.",
                expected="The login activity view reflects current sessions and changes persist after refresh.",
            ),
            TestCase(
                id="SET-04",
                scenario="Block non-admin access to admin routes and keep auth cookies secure.",
                steps="Open /admin as a non-admin user; hit an invalid route; inspect refresh cookie behavior.",
                expected="Non-admin users are redirected away from admin pages, invalid routes fall back safely, and refresh cookies stay HttpOnly and scoped.",
            ),
        ],
    ),
]


def build_html(sections: list[Section]) -> str:
    total_cases = sum(len(section.cases) for section in sections)
    generated_on = date.today().strftime("%B %d, %Y")
    summary_rows = "\n".join(
        f"<tr><td>{escape(section.title)}</td><td>{len(section.cases)}</td></tr>" for section in sections
    )

    section_blocks = []
    for section in sections:
        rows = []
        for case in section.cases:
            rows.append(
                "<tr>"
                f"<td class='case-id'>{escape(case.id)}</td>"
                f"<td class='scenario'>{escape(case.scenario)}</td>"
                f"<td class='steps'>{escape(case.steps)}</td>"
                f"<td class='expected'>{escape(case.expected)}</td>"
                "</tr>"
            )
        section_blocks.append(
            f"""
            <section class="module">
              <h2>{escape(section.title)}</h2>
              <p class="note">{escape(section.note)}</p>
              <table>
                <thead>
                  <tr>
                    <th>Case</th>
                    <th>Scenario</th>
                    <th>Steps</th>
                    <th>Expected Result</th>
                  </tr>
                </thead>
                <tbody>
                  {''.join(rows)}
                </tbody>
              </table>
            </section>
            """
        )

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>SocialSea Test Case Matrix</title>
  <style>
    :root {{
      --bg: #f4f7fb;
      --card: #ffffff;
      --ink: #172033;
      --muted: #5b6579;
      --accent: #0f766e;
      --accent-2: #1f2937;
      --line: #d7deea;
      --soft: #edf6f5;
    }}

    @page {{
      size: A4 landscape;
      margin: 12mm;
    }}

    * {{
      box-sizing: border-box;
    }}

    html, body {{
      margin: 0;
      padding: 0;
      background: var(--bg);
      color: var(--ink);
      font-family: "Segoe UI", Arial, sans-serif;
      line-height: 1.35;
    }}

    body {{
      padding: 24px;
    }}

    .cover {{
      background: linear-gradient(135deg, #0f172a 0%, #134e4a 100%);
      color: #fff;
      border-radius: 18px;
      padding: 28px 30px;
      margin-bottom: 20px;
      box-shadow: 0 10px 30px rgba(15, 23, 42, 0.18);
    }}

    .cover h1 {{
      margin: 0 0 8px;
      font-size: 30px;
      letter-spacing: 0.2px;
    }}

    .cover p {{
      margin: 6px 0 0;
      max-width: 1000px;
      color: rgba(255, 255, 255, 0.92);
      font-size: 13px;
    }}

    .meta {{
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
      margin-top: 18px;
    }}

    .meta-card, .summary-card, section.module {{
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 14px;
      box-shadow: 0 6px 20px rgba(15, 23, 42, 0.05);
    }}

    .meta-card {{
      padding: 12px 14px;
      background: rgba(255, 255, 255, 0.08);
      border-color: rgba(255, 255, 255, 0.18);
      color: #fff;
    }}

    .meta-card span {{
      display: block;
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.12em;
      opacity: 0.8;
      margin-bottom: 4px;
    }}

    .meta-card strong {{
      font-size: 16px;
    }}

    .summary-wrap {{
      display: grid;
      grid-template-columns: 1.2fr 0.8fr;
      gap: 16px;
      margin: 18px 0 24px;
      break-inside: avoid;
    }}

    .summary-card {{
      padding: 16px 18px;
    }}

    .summary-card h2 {{
      margin: 0 0 10px;
      font-size: 18px;
      color: var(--accent-2);
    }}

    .summary-card p {{
      margin: 0;
      color: var(--muted);
      font-size: 12.5px;
    }}

    .summary-table {{
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
      margin-top: 10px;
    }}

    .summary-table th, .summary-table td {{
      border-bottom: 1px solid var(--line);
      text-align: left;
      padding: 8px 10px;
    }}

    .summary-table th {{
      background: var(--soft);
      color: var(--accent-2);
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }}

    section.module {{
      padding: 16px 16px 14px;
      margin-bottom: 16px;
      break-inside: avoid;
      page-break-inside: avoid;
    }}

    section.module h2 {{
      margin: 0 0 6px;
      font-size: 18px;
      color: var(--accent-2);
    }}

    .note {{
      margin: 0 0 12px;
      color: var(--muted);
      font-size: 12px;
    }}

    table {{
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 11px;
    }}

    th, td {{
      border: 1px solid var(--line);
      padding: 8px 9px;
      vertical-align: top;
      word-wrap: break-word;
      overflow-wrap: anywhere;
    }}

    th {{
      background: var(--accent-2);
      color: white;
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }}

    tbody tr:nth-child(even) td {{
      background: #fbfcfe;
    }}

    .case-id {{
      width: 10%;
      font-weight: 700;
      color: var(--accent);
    }}

    .scenario {{
      width: 25%;
    }}

    .steps {{
      width: 32%;
    }}

    .expected {{
      width: 33%;
    }}

    .footer {{
      margin-top: 16px;
      color: var(--muted);
      font-size: 10.5px;
      text-align: center;
    }}
  </style>
</head>
<body>
  <header class="cover">
    <h1>SocialSea Test Case Matrix</h1>
    <p>
      Comprehensive functional and regression test coverage for the current SocialSea codebase.
      This document focuses on the main user journeys visible in the backend and React client,
      with extra attention on auth, feed, messaging, moderation, jobs, emergency tools, and admin flows.
    </p>
    <div class="meta">
      <div class="meta-card">
        <span>Generated</span>
        <strong>{escape(generated_on)}</strong>
      </div>
      <div class="meta-card">
        <span>Modules Covered</span>
        <strong>{len(sections)}</strong>
      </div>
      <div class="meta-card">
        <span>Total Test Cases</span>
        <strong>{total_cases}</strong>
      </div>
    </div>
  </header>

  <div class="summary-wrap">
    <div class="summary-card">
      <h2>Scope</h2>
      <p>
        These test cases are written as broad manual or end-to-end checks that can be executed
        against the current product. They complement the backend unit tests already present in the
        repository and can be expanded into automated suites later.
      </p>
      <p style="margin-top: 10px;">
        Coverage includes the SocialSea landing/auth flows, feed and post actions, profile privacy,
        stories and reels, realtime chat, notifications, anonymous moderation, jobs and resumes,
        live streaming, SOS workflows, vault access, admin tools, and preference settings.
      </p>
    </div>
    <div class="summary-card">
      <h2>Section Count</h2>
      <table class="summary-table">
        <thead>
          <tr>
            <th>Module</th>
            <th>Cases</th>
          </tr>
        </thead>
        <tbody>
          {summary_rows}
        </tbody>
      </table>
    </div>
  </div>

  {''.join(section_blocks)}

  <div class="footer">
    SocialSea test case matrix generated from the current repository feature set on {escape(generated_on)}.
  </div>
</body>
</html>
"""


def main() -> None:
    repo_root = Path(__file__).resolve().parents[1]
    reports_dir = repo_root / "reports"
    reports_dir.mkdir(exist_ok=True)

    html_path = reports_dir / "socialsea-test-cases.html"
    html_path.write_text(build_html(SECTIONS), encoding="utf-8")
    print(html_path)


if __name__ == "__main__":
    main()
