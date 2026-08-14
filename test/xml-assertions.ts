import assert from "node:assert/strict";
import { DOMParser } from "@xmldom/xmldom";

export function assertWellFormedXml(xml: string): void {
  const errors: string[] = [];
  const document = new DOMParser({
    onError: (_level, message) => errors.push(message)
  }).parseFromString(xml, "application/xml");

  assert.deepEqual(errors, []);
  assert.equal(document.documentElement?.nodeName, "qti-assessment-item");
  assert.equal(document.getElementsByTagName("parsererror").length, 0);
}
