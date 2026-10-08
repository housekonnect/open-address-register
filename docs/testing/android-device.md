# Testing the field app on an Android phone

This guide installs the field app's **development build** (an APK) on a low-cost Android phone and runs step 5 of the README slice there: sign in, capture offline, sync, retry. It takes about 30 minutes the first time.

A development build contains the native code (MapLibre, SQLite, camera, location). The JavaScript comes from the Metro server on your computer while you test, so the computer must stay on and reachable.

## What you need

- The phone: Android 7.0 or newer, with a camera and GPS, about 300 MB free.
- The computer that runs the stack (`make up`), on the **same Wi-Fi** as the phone.
- On the computer: the Android SDK (Android Studio installs it) and JDK 17. React Native builds Android with JDK 17; the backend's JDK 25 stays as it is.
- A USB cable is optional. It makes installing and debugging easier.

## 1. Point the stack at your computer's LAN address

The phone cannot reach `localhost` on your computer. Find the computer's LAN IP (macOS: `ipconfig getifaddr en0`; Linux: `hostname -I`), for example `192.168.1.10`.

1. Edit these lines in the **root `.env`**. Change only these values and keep every secret as it is: the data volumes were initialised with them.

   ```sh
   OIDC_PUBLIC_URL=http://192.168.1.10:9000
   PORTAL_PUBLIC_URL=http://192.168.1.10:3000
   TILES_PUBLIC_URL=http://192.168.1.10:3002
   ```

2. Restart: `make down && make up`. The token issuer, the redirect URLs and the map style now use the LAN address.
3. In `apps/field/.env`, set the same host:

   ```sh
   EXPO_PUBLIC_API_URL=http://192.168.1.10:8080
   EXPO_PUBLIC_OIDC_ISSUER=http://192.168.1.10:9000/application/o/ugaddress-field/
   EXPO_PUBLIC_MAP_STYLE_URL=http://192.168.1.10:3000/map/style.json
   ```

4. Check from the phone's browser that http://192.168.1.10:3000 opens the portal. If it doesn't, allow ports 3000, 3002, 8080, 8081 and 9000 in the computer's firewall.

To go back to `localhost` later, restore the three lines in `.env` and run `make down && make up`.

> With a USB cable you can keep `localhost` instead: run `adb reverse tcp:3000 tcp:3000`, and the same for 3002, 8080, 8081 and 9000. The phone then reaches the computer through the cable. The emulator test below works this way.

## 2. Build the APK

```sh
make field-apk
```

This generates the native project (`apps/field/android`, which is git-ignored) and builds `apps/field/android/app/build/outputs/apk/debug/app-debug.apk`. The first build downloads Gradle and the missing SDK parts and takes 10–20 minutes.

The debug APK contains all four CPU architectures (about 200 MB). For a phone only, a smaller build:

```sh
cd apps/field/android && ./gradlew assembleDebug -PreactNativeArchitectures=arm64-v8a,armeabi-v7a
```

## 3. Install it on the phone

- **With USB:**
  1. On the phone, enable *Settings → About phone → tap "Build number" 7 times*, then *Developer options → USB debugging*.
  2. Connect the cable and accept the prompt.
  3. Run `adb install -r apps/field/android/app/build/outputs/apk/debug/app-debug.apk`.
- **Without USB:** copy the APK to the phone (shared folder, messaging app or a download link from your computer), open it, and allow *Install unknown apps* for the app you opened it with.

The app is called **UGAddress Field**.

## 4. Start the JavaScript server

On the computer:

```sh
pnpm --filter @ugaddress/field start
```

Open UGAddress Field on the phone. The development launcher lists the server on your network. If it doesn't, choose *Enter URL manually* and type `http://192.168.1.10:8081`. With USB and `adb reverse`, use `http://localhost:8081`.

## 5. Run step 5: sign in, capture offline, sync, retry

The password of `verifier` is `TEST_USER_PASSWORD` in the root `.env`.

1. **Sign in** as `verifier`. A browser tab opens Authentik; after the password it returns to the app. Field verifiers have no second factor.
2. The map shows the basemap with the demo streets and the "© OpenStreetMap contributors" credit. **My location** moves the map to the phone's position.
3. **Go offline**: switch the phone to airplane mode. Alternatively, stop the API on the computer with `docker compose -f infra/compose/docker-compose.yml --env-file .env stop backend`.
4. Choose **Building**, add a note (no names or phone numbers), **Take photo** and **Save capture**. The capture appears as *Waiting to upload*, and *Offline* is shown. Make two or three captures.
5. **Close the app completely** (swipe it away) and open it again: the captures are still queued, because they are stored in SQLite on the phone.
6. **Go online** again (airplane mode off, or `... start backend`). The queue uploads automatically, or press **Sync now**. Each capture changes to *Uploaded*.
7. **Retry**: press **Sync now** again. Nothing is uploaded twice: each capture carries its own idempotency key. To test a lost response, cut the network during an upload (airplane mode right after pressing **Sync now**), then sync again. On the computer, still exactly one change request per capture:

   ```sh
   docker compose -f infra/compose/docker-compose.yml --env-file .env exec -T postgis psql -U postgres -d register -c \
     "select id, created_at, photo_sha256, location_mocked from register.change_request where source = 'field' order by created_at desc limit 5"
   ```

8. Sign in to the console as `approver` and open **Inbox**: the captures are there with their photo, the SHA-256 computed on the phone and the capture point on the map.

## Mocked locations

The app sends Android's "mocked location" flag with every capture. Such captures are **accepted and flagged** for the approver, never blocked. To try it:

1. Install a mock location app on the phone, for example "Fake GPS location".
2. Choose it in *Developer options → Select mock location app*, and start a fake position.
3. Make a capture. The queue shows *The phone reported a simulated location*. In the console inbox the request has a red **Mocked location** badge and a warning next to the evidence.

## On the Android emulator instead

The same build runs on an emulator (Android Studio → Device Manager; an arm64 image on Apple Silicon). Keep `localhost` everywhere and forward the ports:

```sh
adb install -r apps/field/android/app/build/outputs/apk/debug/app-debug.apk
for port in 3000 3002 8080 8081 9000; do adb reverse tcp:$port tcp:$port; done
adb emu geo fix 32.5948 0.3503     # put the emulator's GPS in the demo area (longitude latitude)
pnpm --filter @ugaddress/field start
```

Without the `geo fix`, the emulator reports a position in California, and the API rejects captures there because they are outside every admin unit. Airplane mode (`adb shell cmd connectivity airplane-mode enable`) makes the app go offline; `adb reverse` keeps working through it, so turning airplane mode off triggers the automatic sync.

The emulator cannot test the mocked-location flag with `adb` alone: expo-location reads positions through Google Play services, which ignore the shell's test location providers. Use a mock location app as described above.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Sign-in shows "invalid redirect URI" or the backend answers 401 | `OIDC_PUBLIC_URL` in `.env` and `EXPO_PUBLIC_OIDC_ISSUER` must use the same host; restart with `make down && make up`. |
| The map stays empty | Open `EXPO_PUBLIC_MAP_STYLE_URL` in the phone's browser; it must return JSON. Check `TILES_PUBLIC_URL` and that `make basemap` has run. |
| Uploads stay at *Waiting to upload* | Open `http://<LAN-IP>:8080/actuator/health` in the phone's browser; check the firewall. |
| "Unable to load script" on start | The Metro server (step 4) is not running or not reachable on port 8081. |
| Save capture says "No location fix yet" | The phone has no GPS fix yet: step outside, wait for the blue dot on the map, and save again. The photo and note are kept. |
| The app is slow on a low-end phone | The development build is not optimised; the release build will be much faster. Captures still work offline. |
