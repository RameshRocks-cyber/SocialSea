import { useState } from "react";
import {
  FiCamera,
  FiChevronDown,
  FiChevronRight,
  FiEye,
  FiFolder,
  FiLock,
  FiMenu,
  FiMic,
  FiMove,
  FiMousePointer,
  FiPenTool,
  FiPlay,
  FiPlus,
  FiSearch,
  FiSettings,
  FiSkipBack,
  FiSkipForward,
  FiSliders,
  FiType,
  FiUpload,
  FiVolume2,
  FiZoomIn
} from "react-icons/fi";
import "./PremiereReferenceLayout.css";

const PREMIERE_MENU_GROUPS = [
  {
    key: "file",
    label: "File",
    items: [
      "New Project",
      "New Sequence",
      "Open Project",
      "Close / Close Project",
      "---",
      "Save",
      "Save As",
      "Save a Copy",
      "---",
      "Import",
      "Import from Media Browser",
      "Export Media",
      "Send to Media Encoder",
      "Get Media File Properties",
      "---",
      "Exit / Quit"
    ]
  },
  {
    key: "edit",
    label: "Edit",
    items: [
      "Undo",
      "Redo",
      "---",
      "Cut",
      "Copy",
      "Paste",
      "Paste Attributes",
      "Clear",
      "Ripple Delete",
      "Duplicate",
      "---",
      "Select All",
      "Deselect All",
      "Find",
      "Edit Original",
      "Keyboard Shortcuts",
      "Preferences / Settings"
    ]
  },
  {
    key: "clip",
    label: "Clip",
    items: [
      "Make Subclip",
      "Audio Channels",
      "Audio Gain",
      "Speed / Duration",
      "---",
      "Insert",
      "Overwrite",
      "Enable",
      "Link",
      "Unlink",
      "Group",
      "Ungroup",
      "Synchronize",
      "Nest",
      "Modify"
    ]
  },
  {
    key: "sequence",
    label: "Sequence",
    items: [
      "Sequence Settings",
      "Render Effects In to Out",
      "Render In to Out",
      "Match Frame",
      "Reverse Match Frame",
      "Add Edit",
      "Add Edit to All Tracks",
      "Trim Edit",
      "Apply Video Transition",
      "Apply Audio Transition",
      "Lift",
      "Extract",
      "Zoom In",
      "Zoom Out",
      "Snap in Timeline",
      "Make Subsequence",
      "Add Caption Track"
    ]
  },
  {
    key: "markers",
    label: "Markers",
    items: [
      "Mark In",
      "Mark Out",
      "Mark Clip",
      "Mark Selection",
      "Go to In",
      "Go to Out",
      "Clear In",
      "Clear Out",
      "Clear In and Out",
      "Add Marker",
      "Go to Next Marker",
      "Go to Previous Marker",
      "Clear Selected Marker",
      "Clear All Markers"
    ]
  },
  {
    key: "graphics",
    label: "Graphics and Titles",
    items: [
      "New Layer",
      "Text",
      "Rectangle",
      "Ellipse",
      "Arrange",
      "Bring to Front",
      "Bring Forward",
      "Send Backward",
      "Send to Back",
      "Select Next Layer",
      "Select Previous Layer",
      "Export as Motion Graphics Template"
    ]
  },
  {
    key: "view",
    label: "View",
    items: [
      "Show Rulers",
      "Show Guides",
      "Add Guide",
      "Lock Guides",
      "Clear Guides",
      "Snap in Program Monitor",
      "Safe Margins",
      "Display Mode",
      "Zoom / Magnification",
      "Timecode Display Options"
    ]
  },
  {
    key: "window",
    label: "Window",
    items: [
      "Workspaces",
      "All Panels",
      "Assembly",
      "Audio",
      "Captions and Graphics",
      "Color",
      "Editing",
      "Effects",
      "Essential",
      "Learning",
      "Reset to Saved Layout",
      "---",
      "Audio Clip Mixer",
      "Audio Track Mixer",
      "Effect Controls",
      "Effects",
      "Media Browser",
      "Program Monitor",
      "Projects",
      "Source Monitor",
      "Timeline"
    ]
  },
  {
    key: "help",
    label: "Help",
    items: [
      "Premiere Pro Help",
      "Learn / Tutorials",
      "What's New",
      "System Compatibility Report",
      "Manage Account / Sign In",
      "Updates",
      "About Premiere Pro"
    ]
  }
];

const WORKSPACE_TABS = [
  "Learning",
  "Assembly",
  "Editing",
  "Color",
  "Effects",
  "Audio",
  "Captions and Graphics",
  "Libraries"
];

const PROJECT_MEDIA = [
  { name: "Mountains.mp4", time: "15:00", type: "mountains" },
  { name: "Lake.mp4", time: "12:18", type: "lake" },
  { name: "Sunset.mp4", time: "10:11", type: "sunset" },
  { name: "Forest.mp4", time: "8:23", type: "forest" },
  { name: "City.mp4", time: "14:07", type: "city" },
  { name: "Audio Track.wav", time: "3:15", type: "audio" }
];

const TOOLBAR_TOOLS = [
  { key: "selection", label: "Selection Tool", icon: <FiMousePointer /> },
  { key: "track", label: "Track Select Tool", icon: "↕" },
  { key: "ripple", label: "Ripple Edit Tool", icon: "⇄" },
  { key: "razor", label: "Razor Tool", icon: "◆" },
  { key: "slip", label: "Slip Tool", icon: "↔" },
  { key: "pen", label: "Pen Tool", icon: <FiPenTool /> },
  { key: "type", label: "Type Tool", icon: <FiType /> },
  { key: "hand", label: "Hand Tool", icon: "✋" },
  { key: "zoom", label: "Zoom Tool", icon: <FiZoomIn /> }
];

const EFFECT_FOLDERS = [
  {
    label: "Presets",
    items: ["Cinematic Preset", "Fast Blur Preset", "Soft Fade Preset"]
  },
  {
    label: "Lumetri Presets",
    items: ["Cinematic Look", "Warm Look", "Cool Look"]
  },
  {
    label: "Audio Effects",
    items: ["Balance", "Bass", "Reverb", "Noise Reduction"]
  },
  {
    label: "Audio Transitions",
    items: ["Constant Gain", "Constant Power", "Exponential Fade"]
  },
  {
    label: "Video Effects",
    items: ["Blur", "Color Correction", "Distort", "Keying", "Transform"]
  },
  {
    label: "Video Transitions",
    items: ["Cross Dissolve", "Dip to Black", "Slide", "Wipe"]
  }
];

const monitorButtons = [
  { label: "Mark In", icon: "{" },
  { label: "Add Marker", icon: "|" },
  { label: "Mark Out", icon: "}" },
  { label: "Go to In", icon: <FiSkipBack /> },
  { label: "Step Back", icon: "◀" },
  { label: "Play", icon: <FiPlay /> },
  { label: "Step Forward", icon: "▶" },
  { label: "Go to Out", icon: <FiSkipForward /> },
  { label: "Insert / Overwrite", icon: "▦" },
  { label: "Export Frame", icon: <FiCamera /> },
  { label: "Button Editor", icon: <FiSliders /> },
  { label: "Add Button", icon: <FiPlus /> }
];

function Callout({ children, className = "", side = "top" }) {
  return <div className={`pp-callout pp-callout-${side} ${className}`}>{children}</div>;
}

function PanelHeader({ title }) {
  return (
    <div className="pp-panel-header">
      <span>{title}</span>
      <FiMenu />
    </div>
  );
}

function PremiereMenuBar() {
  const [openMenu, setOpenMenu] = useState("");

  return (
    <div className="pp-menu-row">
      <div className="pp-app-logo">Pr</div>

      <nav className="pp-main-menu" onMouseLeave={() => setOpenMenu("")}>
        {PREMIERE_MENU_GROUPS.map((group) => (
          <div className="pp-menu-item-wrap" key={group.key}>
            <button
              type="button"
              className={`pp-menu-item ${openMenu === group.key ? "active" : ""}`}
              onMouseEnter={() => setOpenMenu(group.key)}
              onClick={() => setOpenMenu(openMenu === group.key ? "" : group.key)}
            >
              {group.label}
            </button>

            {openMenu === group.key && (
              <div className="pp-menu-dropdown">
                {group.items.map((item, index) =>
                  item === "---" ? (
                    <div className="pp-menu-separator" key={`${group.key}-${index}`} />
                  ) : (
                    <button type="button" key={`${group.key}-${item}`}>
                      {item}
                    </button>
                  )
                )}
              </div>
            )}
          </div>
        ))}
      </nav>
    </div>
  );
}

function WorkspaceTabs() {
  return (
    <div className="pp-workspace-tabs">
      {WORKSPACE_TABS.map((tab) => (
        <button type="button" key={tab} className={tab === "Editing" ? "active" : ""}>
          {tab}
        </button>
      ))}
      <div className="pp-workspace-icons">
        <FiUpload />
        <FiMenu />
      </div>
    </div>
  );
}

function MonitorControlBar({ current, end }) {
  return (
    <div className="pp-monitor-controls">
      <div className="pp-monitor-readout">
        <strong className="active-time">{current}</strong>
        <button type="button">Fit <FiChevronDown /></button>
        <button type="button">1/2 <FiChevronDown /></button>
        <FiSettings className="pp-wrench" />
        <strong>{end}</strong>
      </div>

      <div className="pp-monitor-ruler">
        <div className="pp-monitor-playhead" />
      </div>

      <div className="pp-monitor-buttons">
        {monitorButtons.map((item) => (
          <button type="button" key={item.label} title={item.label}>
            {item.icon}
          </button>
        ))}
      </div>
    </div>
  );
}

function SourceMonitor() {
  return (
    <section className="pp-panel pp-monitor pp-source-monitor">
      <PanelHeader title="Source: Mountains.mp4" />
      <div className="pp-monitor-preview pp-preview-mountains">
        <Callout side="inside">Source Monitor</Callout>
      </div>
      <MonitorControlBar current="00:00:08:12" end="00:00:15:00" />
    </section>
  );
}

function ProgramMonitor() {
  return (
    <section className="pp-panel pp-monitor pp-program-monitor">
      <PanelHeader title="Program: Sequence 01" />
      <div className="pp-monitor-preview pp-preview-sunset">
        <Callout side="inside">Program Monitor</Callout>
      </div>
      <MonitorControlBar current="00:00:24:05" end="00:00:59:22" />
    </section>
  );
}

function ProjectPanel() {
  return (
    <section className="pp-panel pp-project-panel">
      <div className="pp-project-tabs">
        <strong>Project: My Project</strong>
        <button type="button">Media Browser</button>
        <button type="button">Libraries</button>
        <button type="button">Info</button>
      </div>

      <div className="pp-project-file">
        <span>▹ My Project.prproj</span>
        <span>1 of 12 items selected</span>
      </div>

      <div className="pp-project-search">
        <FiSearch />
      </div>

      <div className="pp-media-grid">
        {PROJECT_MEDIA.map((item) => (
          <button type="button" className="pp-media-card" key={item.name}>
            <span className={`pp-media-thumb pp-media-${item.type}`}>
              {item.type === "audio" && <span className="pp-thumb-wave" />}
            </span>
            <span className="pp-media-meta">
              <em>{item.name}</em>
              <small>{item.time}</small>
            </span>
          </button>
        ))}
      </div>

      <div className="pp-project-footer">
        <span>▦</span>
        <span>▤</span>
        <span>▢</span>
        <span className="pp-project-slider" />
        <span>⌘</span>
        <span>⌫</span>
      </div>

      <Callout side="bottom" className="pp-project-label">
        Project Panel / Media
      </Callout>
    </section>
  );
}

function VerticalToolbar() {
  return (
    <aside className="pp-vertical-toolbar">
      {TOOLBAR_TOOLS.map((tool, index) => (
        <button
          type="button"
          key={tool.key}
          title={tool.label}
          className={index === 0 ? "active" : ""}
        >
          {tool.icon}
        </button>
      ))}

      <Callout side="right" className="pp-toolbar-label">
        Toolbar
      </Callout>
    </aside>
  );
}

function TimelinePanel() {
  return (
    <section className="pp-panel pp-timeline-panel">
      <div className="pp-timeline-top">
        <div>
          <PanelHeader title="Sequence 01" />
          <strong className="pp-timeline-time">00:00:24:05</strong>
        </div>
        <div className="pp-timeline-top-icons">
          <span title="Snap">🧲</span>
          <span title="Linked Selection">🔗</span>
          <span title="Selection Follows Playhead">▣</span>
          <span title="Markers">◆</span>
          <span title="Closed Captions">CC</span>
        </div>
      </div>

      <div className="pp-timeline-grid">
        <div className="pp-track-labels">
          <div className="pp-track-head" />
          {["V2", "V1", "A1", "A2"].map((track, index) => (
            <div className="pp-track-row" key={track}>
              <span className={index === 1 || index > 1 ? "active" : ""}>{track}</span>
              <span>{track}</span>
              <small>
                {index < 2 ? (
                  <>
                    <FiLock /> <FiEye />
                  </>
                ) : (
                  <>
                    M&nbsp; S&nbsp; <FiMic />
                  </>
                )}
              </small>
            </div>
          ))}
        </div>

        <div className="pp-timeline-editor">
          <div className="pp-timeline-ruler">
            <span>00:00:00:00</span>
            <span>00:00:15:00</span>
            <span>00:00:30:00</span>
            <span>00:00:45:00</span>
            <span>00:01:00:00</span>
            <div className="pp-yellow-render-bar" />
          </div>

          <div className="pp-timeline-tracks">
            <div className="pp-lane">
              <div className="pp-clip pp-purple" style={{ left: "5%", width: "31%" }}>Mountains.mp4</div>
              <div className="pp-clip pp-purple" style={{ left: "51%", width: "24%" }}>City.mp4</div>
            </div>
            <div className="pp-lane">
              <div className="pp-clip pp-blue" style={{ left: "4%", width: "23%" }}>Lake.mp4</div>
              <div className="pp-clip pp-blue" style={{ left: "27%", width: "26%" }}>Sunset.mp4</div>
              <div className="pp-clip pp-blue" style={{ left: "53%", width: "32%" }}>Forest.mp4</div>
            </div>
            <div className="pp-lane">
              <div className="pp-clip pp-green" style={{ left: "4%", width: "81%" }}>
                <span className="pp-wave" />
                <b>fx</b>
              </div>
            </div>
            <div className="pp-lane">
              <div className="pp-clip pp-green" style={{ left: "4%", width: "81%" }}>
                <span className="pp-wave" />
                <b>fx</b>
              </div>
            </div>

            <div className="pp-playhead" />
          </div>
        </div>
      </div>

      <Callout side="bottom" className="pp-timeline-label">
        Timeline
      </Callout>
    </section>
  );
}

function EffectsPanel() {
  return (
    <section className="pp-side-panel pp-effects-panel">
      <PanelHeader title="Effects" />
      <div className="pp-search-box">
        <FiSearch />
      </div>

      <div className="pp-folder-list">
        {EFFECT_FOLDERS.map((folder) => (
          <details key={folder.label} open={folder.label === "Video Effects"}>
            <summary>
              <FiChevronRight />
              <FiFolder />
              {folder.label}
            </summary>
            <div className="pp-sub-folder-list">
              {folder.items.map((item) => (
                <button type="button" key={item}>{item}</button>
              ))}
            </div>
          </details>
        ))}
      </div>
    </section>
  );
}

function EffectControlsPanel() {
  const rows = [
    { name: "Motion", sub: ["Position 960, 540", "Scale 100%", "Rotation 0°"] },
    { name: "Opacity", sub: ["Opacity 100%", "Blend Mode Normal"] },
    { name: "Time Remapping", sub: ["Speed 100%"] }
  ];

  return (
    <section className="pp-side-panel pp-effect-controls-panel">
      <PanelHeader title="Effect Controls" />
      <div className="pp-effect-source">Source • Mountains.mp4 <FiChevronDown /></div>
      <div className="pp-effect-section-title">Video <FiChevronDown /></div>

      {rows.map((row) => (
        <div className="pp-effect-control-group" key={row.name}>
          <button type="button" className="pp-effect-row">
            <span>fx</span>
            <strong>{row.name}</strong>
            <span>↻</span>
          </button>
          <div className="pp-effect-subrows">
            {row.sub.map((sub) => (
              <span key={sub}>{sub}</span>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}

function EssentialGraphicsPanel() {
  return (
    <section className="pp-side-panel pp-graphics-panel">
      <PanelHeader title="Essential Graphics" />
      <div className="pp-tabs">
        <button type="button" className="active">Browse</button>
        <button type="button">Edit</button>
      </div>
      <div className="pp-template-tabs">
        <button type="button" className="active">My Templates</button>
        <button type="button">St Adobe Stock</button>
      </div>
      <label><input type="checkbox" /> Local Templates Folder</label>
      <label><input type="checkbox" /> Libraries</label>

      <div className="pp-template-grid">
        <div>
          <div className="pp-title-card">
            <strong>TITLE HERE</strong>
            <small>SUBTITLE HERE</small>
          </div>
          <span>Modern Lower Third</span>
        </div>
        <div>
          <div className="pp-title-card pp-title-card-bold">
            <strong>TITLE HERE</strong>
          </div>
          <span>Bold Title</span>
        </div>
      </div>
    </section>
  );
}

function AudioPanel() {
  return (
    <section className="pp-side-panel pp-audio-panel">
      <PanelHeader title="Audio" />
      <div className="pp-tabs">
        <button type="button" className="active">Browse</button>
        <button type="button">Edit</button>
      </div>

      <div className="pp-audio-top">
        <span>Audio 1</span>
        <select>
          <option>Mix</option>
          <option>Clip Volume</option>
          <option>Track Volume</option>
        </select>
      </div>

      <div className="pp-audio-meter-wrap">
        <div className="pp-audio-meter">
          {Array.from({ length: 12 }).map((_, index) => (
            <span key={index} style={{ height: `${28 + ((index * 17) % 55)}%` }} />
          ))}
        </div>
        <div className="pp-db-scale">
          <span>0</span>
          <span>-12</span>
          <span>-24</span>
          <span>-36</span>
          <span>-48</span>
          <span>-60</span>
          <span>dB</span>
        </div>
      </div>

      <div className="pp-audio-buttons">
        <button type="button">S</button>
        <button type="button">S</button>
        <button type="button">M</button>
        <span />
      </div>
    </section>
  );
}

function RightColumn() {
  return (
    <aside className="pp-right-column-wrap">
      <div className="pp-right-column">
        <EffectsPanel />
        <EffectControlsPanel />
        <EssentialGraphicsPanel />
        <AudioPanel />
      </div>

      <div className="pp-right-callouts">
        <Callout side="right-column">Effects</Callout>
        <Callout side="right-column">Effect Controls</Callout>
        <Callout side="right-column">Essential Graphics</Callout>
        <Callout side="right-column">Audio</Callout>
      </div>
    </aside>
  );
}

export default function PremiereReferenceLayout() {
  return (
    <main className="premiere-reference-page">
      <h1>Adobe Premiere Pro Layout</h1>

      <div className="pp-window">
        <div className="pp-window-top">
          <div className="pp-traffic">
            <span />
            <span />
            <span />
          </div>
        </div>

        <Callout side="menu" className="pp-menu-label">
          Menu Bar
        </Callout>

        <PremiereMenuBar />
        <WorkspaceTabs />

        <div className="pp-layout-grid">
          <div className="pp-left-main">
            <div className="pp-monitor-grid">
              <SourceMonitor />
              <ProgramMonitor />
            </div>

            <div className="pp-bottom-grid">
              <ProjectPanel />
              <VerticalToolbar />
              <TimelinePanel />
            </div>
          </div>

          <RightColumn />
        </div>
      </div>
    </main>
  );
}
