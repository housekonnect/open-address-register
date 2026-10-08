import { photoSha256, toHex } from "./photo-hash";

describe("photoSha256", () => {
  it("hashes exactly the photo bytes and returns lower-case hex", async () => {
    // GIVEN photo bytes and a digest function (expo-crypto's SHA-256 on the device)
    const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
    const seen: Uint8Array[] = [];
    const digest = async (data: Uint8Array<ArrayBuffer>) => {
      seen.push(data);
      return new Uint8Array([0xba, 0x78, 0x16, 0xbf]).buffer;
    };
    // WHEN the photo is hashed
    const hash = await photoSha256(bytes, digest);
    // THEN the digest saw the photo bytes and its result is returned as hex
    expect(seen).toEqual([bytes]);
    expect(hash).toBe("ba7816bf");
  });

  it("keeps leading zeros of every byte", () => {
    // GIVEN a digest with small byte values WHEN it is formatted THEN every byte has two digits
    expect(toHex(new Uint8Array([0, 1, 15, 16, 255]).buffer)).toBe("00010f10ff");
  });
});
