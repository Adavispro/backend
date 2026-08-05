"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CircleNotch, SignOut } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { logout } from "@/features/auth/api";

export default function LogoutButton() {
  const router = useRouter();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const handleLogout = async () => {
    if (isLoggingOut) return;

    setIsLoggingOut(true);
    setErrorMessage("");

    try {
      await logout();
      router.replace(ROUTES.login);
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError
          ? error.message
          : "Unable to log out. Please try again.",
      );
      setIsLoggingOut(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={handleLogout}
        disabled={isLoggingOut}
        aria-label={isLoggingOut ? "Logging out" : "Logout"}
        title="Logout"
        className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-[#3f464f] transition-colors hover:bg-white/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 disabled:cursor-wait disabled:opacity-60"
      >
        {isLoggingOut ? (
          <CircleNotch className="animate-spin" size={16} weight="bold" />
        ) : (
          <SignOut size={17} weight="regular" />
        )}
      </button>

      <Snackbar
        open={Boolean(errorMessage)}
        variant="error"
        title="Logout failed"
        message={errorMessage}
        onClose={() => setErrorMessage("")}
      />
    </>
  );
}
