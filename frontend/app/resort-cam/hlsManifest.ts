/** Some cameras advertise a fixed decimal AVC codec/level that disagrees with SPS.
 * Leave modern codec declarations intact; let the player inspect TS for this legacy hint.
 */
export function normalizeLegacyAvcManifest(manifest: string): string {
  return manifest.replace(/(^#EXT-X-STREAM-INF:[^\r\n]*),CODECS="avc1\.\d+\.\d+"(?=,|\r?$)/gm, "$1");
}
