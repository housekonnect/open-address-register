/** UI strings, English first; a Luganda catalogue will be added with the same keys. */
const en = {
  "app.title": "Address field capture",
  "signIn.intro": "Sign in with your field verifier account. Captures are stored on the phone and uploaded when you are online.",
  "signIn.button": "Sign in",
  "signIn.failed": "Signing in did not complete. Please try again.",
  "signOut": "Sign out",
  "capture.title": "New capture",
  "capture.kind": "What are you capturing?",
  "capture.kind.building": "Building",
  "capture.kind.entrance": "Entrance",
  "capture.kind.landmark": "Landmark",
  "capture.kind.facility": "Facility",
  "capture.note": "Note about the place (no names or phone numbers)",
  "capture.photo": "Take photo",
  "capture.photoTaken": "Photo taken",
  "capture.save": "Save capture",
  "capture.saved": "Saved on the phone. It will be uploaded when you are online.",
  "capture.needPhoto": "Take a photo first.",
  "capture.locationDenied": "Location permission is needed to record where the capture was made.",
  "capture.cameraDenied": "Camera permission is needed to photograph the building.",
  "capture.locationUnavailable": "No location fix yet. Step outside or wait a moment, then save again.",
  "capture.photoFailed": "The photo could not be stored on the phone. Please take it again.",
  "queue.title": "Captures on this phone",
  "queue.empty": "No captures yet.",
  "queue.sync": "Sync now",
  "queue.syncing": "Syncing…",
  "queue.offline": "Offline: captures will be uploaded automatically when the connection returns.",
  "queue.status.pending": "Waiting to upload",
  "queue.status.synced": "Uploaded",
  "queue.status.failed": "Rejected",
  "queue.report": "{synced} uploaded, {remaining} waiting",
  "queue.needsLogin": "Please sign in again to upload.",
  "queue.locationMocked": "The phone reported a simulated location; the approver will be told.",
  "map.label": "Map around your position",
  "map.myLocation": "My location",
  "map.attribution": "© OpenStreetMap contributors",
} as const;

export type MessageKey = keyof typeof en;

/** Returns the message for a key, replacing {placeholders}. */
export function t(key: MessageKey, params: Record<string, string | number> = {}): string {
  return en[key].replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? `{${name}}`));
}
