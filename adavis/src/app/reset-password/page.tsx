import { Suspense } from "react";
import ResetPasswordScreen from "@/features/auth/screens/ResetPasswordScreen";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordScreen />
    </Suspense>
  );
}
