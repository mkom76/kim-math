/**
 * Mirror of the backend `TopicNormalizer` for display-time convenience.
 * Storage is always the canonical `›`-joined path; this helper just splits
 * it back into levels and joins arrays. The backend remains authoritative —
 * never use this for write normalization.
 */

export const TOPIC_SEPARATOR = '›' // ›
export const TOPIC_JOIN_DELIM = ` ${TOPIC_SEPARATOR} `
export const TOPIC_MAX_LEVELS = 5

/** Parse any of `/`, `>`, `›` separators. Returns trimmed non-empty segments. */
export function parseTopicPath(raw: string | null | undefined): string[] {
  if (!raw) return []
  return raw
    .split(/\s*[/>›]\s*/)
    .map(s => s.trim())
    .filter(s => s.length > 0)
    .slice(0, TOPIC_MAX_LEVELS)
}

export function joinTopicLevels(levels: string[]): string {
  return levels.filter(s => s && s.trim().length > 0).join(TOPIC_JOIN_DELIM)
}
