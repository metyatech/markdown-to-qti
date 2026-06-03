const XML_ESCAPES: Record<string, string> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&apos;"
};

/**
 * Escapes the five XML reserved characters. Mirrors the reference
 * implementation's escapeXml: &, <, >, ", and ' are replaced; all other
 * characters are preserved verbatim.
 */
export function escapeXml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => XML_ESCAPES[char] ?? char);
}
