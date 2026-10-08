import { useMemo } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { t } from "../i18n";
import { useTokens, type Tokens } from "../theme/tokens";

export function SignInScreen({ onSignIn, disabled, failed }: { onSignIn: () => void; disabled: boolean; failed: boolean }) {
  const tokens = useTokens();
  const styles = useMemo(() => createStyles(tokens), [tokens]);
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>{t("app.title")}</Text>
      <Text style={styles.intro}>{t("signIn.intro")}</Text>
      {failed && <Text style={styles.error}>{t("signIn.failed")}</Text>}
      <Pressable accessibilityRole="button" disabled={disabled} onPress={onSignIn} style={[styles.button, disabled && styles.disabled]}>
        <Text style={styles.buttonText}>{t("signIn.button")}</Text>
      </Pressable>
    </View>
  );
}

function createStyles(tokens: Tokens) {
  return StyleSheet.create({
    screen: { flex: 1, justifyContent: "center", padding: 24, gap: 16, backgroundColor: tokens.background },
    title: { fontSize: 24, fontWeight: "600", color: tokens.foreground },
    intro: { color: tokens.mutedForeground, fontSize: 16 },
    error: { color: tokens.destructive },
    button: { backgroundColor: tokens.primary, padding: 14, borderRadius: 8, alignItems: "center" },
    disabled: { opacity: 0.5 },
    buttonText: { color: tokens.primaryForeground, fontWeight: "600", fontSize: 16 },
  });
}
