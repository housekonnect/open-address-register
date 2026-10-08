/** Computes a digest of some bytes, e.g. expo-crypto's `digest(CryptoDigestAlgorithm.SHA256, …)`. */
export type DigestFunction = (data: Uint8Array<ArrayBuffer>) => Promise<ArrayBuffer>;

/** Lower-case hex of a digest, the form the API expects in `photoSha256`. */
export function toHex(digest: ArrayBuffer): string {
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * SHA-256 of a photo's bytes, computed on the device when the photo is taken. The backend re-hashes the stored
 * object and rejects the capture if the two differ.
 */
export async function photoSha256(bytes: Uint8Array<ArrayBuffer>, sha256: DigestFunction): Promise<string> {
  return toHex(await sha256(bytes));
}
