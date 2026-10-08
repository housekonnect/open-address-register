import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { useAuth } from "./auth/useAuth";
import { CaptureScreen } from "./screens/CaptureScreen";
import { SignInScreen } from "./screens/SignInScreen";
import { useTokens } from "./theme/tokens";
import { useCaptureQueue } from "./useCaptureQueue";

export default function App() {
  const tokens = useTokens();
  const auth = useAuth();
  const queue = useCaptureQueue(auth.getAccessToken, auth.signedIn);
  return (
    <SafeAreaProvider>
      <SafeAreaView style={{ flex: 1, backgroundColor: tokens.background }}>
        <StatusBar style="auto" />
        {auth.signedIn ? (
          <CaptureScreen
            queue={queue}
            needsLogin={queue.lastReport?.stoppedBy === "unauthorized"}
            onSignOut={() => void auth.signOut()}
          />
        ) : (
          <SignInScreen onSignIn={auth.signIn} disabled={!auth.ready} failed={auth.error} />
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
