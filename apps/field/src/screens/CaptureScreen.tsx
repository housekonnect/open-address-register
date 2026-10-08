import { Camera, Map, UserLocation, type CameraRef } from "@maplibre/maplibre-react-native";
import * as Crypto from "expo-crypto";
import { Directory, File, Paths } from "expo-file-system";
import * as ImagePicker from "expo-image-picker";
import * as Location from "expo-location";
import { useMemo, useRef, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { photoSha256 } from "../capture/photo-hash";
import { config } from "../config";
import { t, type MessageKey } from "../i18n";
import type { CaptureKind, QueuedCapture } from "../queue/types";
import { useMapStyleUrl, useTokens, type Tokens } from "../theme/tokens";
import type { useCaptureQueue } from "../useCaptureQueue";

const KINDS: CaptureKind[] = ["building", "entrance", "landmark", "facility"];

interface Props {
  queue: ReturnType<typeof useCaptureQueue>;
  needsLogin: boolean;
  onSignOut: () => void;
}

interface KeptPhoto {
  uri: string;
  sha256: string;
}

/**
 * Copies the photo into the app's document directory, so it survives until the capture is uploaded, and hashes
 * the copy's bytes there and then: the backend compares this SHA-256 with the photo it stores.
 */
async function keepPhoto(uri: string): Promise<KeptPhoto> {
  const directory = new Directory(Paths.document, "captures");
  if (!directory.exists) directory.create({ intermediates: true, idempotent: true });
  const target = new File(directory, `${Crypto.randomUUID()}.jpg`);
  new File(uri).copy(target);
  const sha256 = await photoSha256(await target.bytes(), (data) =>
    Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, data),
  );
  return { uri: target.uri, sha256 };
}

export function CaptureScreen({ queue, needsLogin, onSignOut }: Props) {
  const tokens = useTokens();
  const styles = useMemo(() => createStyles(tokens), [tokens]);
  const mapStyleUrl = useMapStyleUrl(config.mapStyleUrl);
  const camera = useRef<CameraRef>(null);
  const [kind, setKind] = useState<CaptureKind>("building");
  const [note, setNote] = useState("");
  const [photo, setPhoto] = useState<KeptPhoto>();
  const [message, setMessage] = useState<string>();

  async function takePhoto() {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) return setMessage(t("capture.cameraDenied"));
    const result = await ImagePicker.launchCameraAsync({ quality: 0.6, exif: false });
    if (!result.canceled && result.assets[0]) setPhoto(await keepPhoto(result.assets[0].uri));
  }

  async function showMyLocation() {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (!permission.granted) return setMessage(t("capture.locationDenied"));
    const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    camera.current?.easeTo({ center: [position.coords.longitude, position.coords.latitude], zoom: 17, duration: 500 });
  }

  async function save() {
    if (!photo) return setMessage(t("capture.needPhoto"));
    const permission = await Location.requestForegroundPermissionsAsync();
    if (!permission.granted) return setMessage(t("capture.locationDenied"));
    const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
    await queue.enqueue({
      longitude: position.coords.longitude,
      latitude: position.coords.latitude,
      accuracyMeters: position.coords.accuracy,
      kind,
      note: note.trim() === "" ? null : note.trim(),
      photoUri: photo.uri,
      photoSha256: photo.sha256,
    });
    setNote("");
    setPhoto(undefined);
    setMessage(t("capture.saved"));
  }

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <Text style={styles.title}>{t("app.title")}</Text>
        <Pressable accessibilityRole="button" onPress={onSignOut}>
          <Text style={styles.link}>{t("signOut")}</Text>
        </Pressable>
      </View>
      <View style={styles.map} accessibilityLabel={t("map.label")}>
        <Map mapStyle={mapStyleUrl} style={StyleSheet.absoluteFill} attribution={false} logo={false}>
          <Camera ref={camera} initialViewState={{ center: config.defaultCenter, zoom: 16 }} />
          <UserLocation />
        </Map>
        <Pressable accessibilityRole="button" style={styles.mapButton} onPress={() => void showMyLocation()}>
          <Text style={styles.mapButtonText}>{t("map.myLocation")}</Text>
        </Pressable>
        <Text style={styles.attribution}>{t("map.attribution")}</Text>
      </View>
      <Text style={styles.section}>{t("capture.title")}</Text>
      <Text style={styles.label}>{t("capture.kind")}</Text>
      <View style={styles.row}>
        {KINDS.map((k) => (
          <Pressable
            key={k}
            accessibilityRole="radio"
            accessibilityState={{ selected: k === kind }}
            onPress={() => setKind(k)}
            style={[styles.chip, k === kind && styles.chipSelected]}
          >
            <Text style={k === kind ? styles.chipTextSelected : styles.chipText}>{t(`capture.kind.${k}` as MessageKey)}</Text>
          </Pressable>
        ))}
      </View>
      <TextInput
        style={styles.input}
        placeholder={t("capture.note")}
        placeholderTextColor={tokens.mutedForeground}
        value={note}
        onChangeText={setNote}
        maxLength={500}
      />
      <View style={styles.row}>
        <Pressable accessibilityRole="button" style={styles.secondaryButton} onPress={() => void takePhoto()}>
          <Text style={styles.secondaryButtonText}>{photo ? t("capture.photoTaken") : t("capture.photo")}</Text>
        </Pressable>
        <Pressable accessibilityRole="button" style={styles.button} onPress={() => void save()}>
          <Text style={styles.buttonText}>{t("capture.save")}</Text>
        </Pressable>
      </View>
      {message && <Text style={styles.muted}>{message}</Text>}
      <View style={styles.header}>
        <Text style={styles.section}>{t("queue.title")}</Text>
        <Pressable accessibilityRole="button" disabled={queue.syncing} onPress={() => void queue.sync()}>
          <Text style={styles.link}>{queue.syncing ? t("queue.syncing") : t("queue.sync")}</Text>
        </Pressable>
      </View>
      {!queue.online && <Text style={styles.muted}>{t("queue.offline")}</Text>}
      {needsLogin && <Text style={styles.error}>{t("queue.needsLogin")}</Text>}
      {queue.lastReport && (
        <Text style={styles.muted}>{t("queue.report", { synced: queue.lastReport.synced, remaining: queue.lastReport.remaining })}</Text>
      )}
      <FlatList
        data={queue.captures}
        keyExtractor={(item) => item.id}
        ListEmptyComponent={<Text style={styles.muted}>{t("queue.empty")}</Text>}
        renderItem={({ item }) => <QueueItem item={item} styles={styles} />}
      />
    </View>
  );
}

function QueueItem({ item, styles }: { item: QueuedCapture; styles: ReturnType<typeof createStyles> }) {
  return (
    <View style={styles.item}>
      <Text style={styles.itemTitle}>
        {t(`capture.kind.${item.kind}` as MessageKey)} · {new Date(item.capturedAt).toLocaleString()}
      </Text>
      <Text style={item.status === "failed" ? styles.error : styles.muted}>
        {t(`queue.status.${item.status}`)}
        {item.lastError && item.status !== "synced" ? ` (${item.lastError})` : ""}
      </Text>
    </View>
  );
}

function createStyles(tokens: Tokens) {
  return StyleSheet.create({
    screen: { flex: 1, backgroundColor: tokens.background, padding: 16, gap: 8 },
    header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
    title: { fontSize: 20, fontWeight: "600", color: tokens.foreground },
    section: { fontSize: 16, fontWeight: "600", color: tokens.foreground, marginTop: 8 },
    label: { color: tokens.mutedForeground },
    link: { color: tokens.primary, fontWeight: "600" },
    map: { height: 220, borderRadius: 8, overflow: "hidden", borderWidth: 1, borderColor: tokens.border },
    mapButton: { position: "absolute", top: 8, right: 8, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6, backgroundColor: tokens.card, borderWidth: 1, borderColor: tokens.border },
    mapButtonText: { color: tokens.foreground, fontWeight: "600" },
    attribution: { position: "absolute", bottom: 0, right: 0, paddingHorizontal: 4, fontSize: 10, color: tokens.foreground, backgroundColor: tokens.card },
    row: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
    chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, borderWidth: 1, borderColor: tokens.border, backgroundColor: tokens.card },
    chipSelected: { backgroundColor: tokens.primary, borderColor: tokens.primary },
    chipText: { color: tokens.foreground },
    chipTextSelected: { color: tokens.primaryForeground },
    input: { borderWidth: 1, borderColor: tokens.border, borderRadius: 8, padding: 10, color: tokens.foreground, backgroundColor: tokens.card },
    button: { flex: 1, backgroundColor: tokens.primary, padding: 12, borderRadius: 8, alignItems: "center" },
    buttonText: { color: tokens.primaryForeground, fontWeight: "600" },
    secondaryButton: { flex: 1, borderWidth: 1, borderColor: tokens.border, padding: 12, borderRadius: 8, alignItems: "center", backgroundColor: tokens.card },
    secondaryButtonText: { color: tokens.foreground, fontWeight: "600" },
    muted: { color: tokens.mutedForeground },
    error: { color: tokens.destructive },
    item: { paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: tokens.border },
    itemTitle: { color: tokens.foreground },
  });
}
