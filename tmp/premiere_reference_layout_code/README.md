# Premiere Reference Layout

This is a standalone React layout that recreates the Adobe Premiere Pro-style wireframe shown in your reference image.

## Files

- `PremiereReferenceLayout.jsx`
- `PremiereReferenceLayout.css`

## Usage

1. Copy both files into your React project, for example:

```txt
src/components/PremiereReferenceLayout.jsx
src/components/PremiereReferenceLayout.css
```

2. Import and render it:

```jsx
import PremiereReferenceLayout from "./components/PremiereReferenceLayout";

export default function App() {
  return <PremiereReferenceLayout />;
}
```

3. Make sure your project has `react-icons` installed:

```bash
npm install react-icons
```

## What is included

- Menu Bar with dropdown sub-options
- Workspace tabs
- Source Monitor
- Program Monitor
- Project Panel / Media
- Vertical Toolbar
- Timeline
- Effects
- Effect Controls
- Essential Graphics
- Audio
- Purple callout labels and matching colors
