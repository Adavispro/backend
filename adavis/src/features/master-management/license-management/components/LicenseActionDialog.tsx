"use client";

import { useState } from "react";
import { Button, Dialog } from "@/components/ui";

export type LicenseDialogAction = "ACTIVATE" | "SUSPEND" | "REACTIVATE" | "UPGRADE";

interface LicenseActionDialogProps {
  action: LicenseDialogAction | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (values: { encryptedLicenseToken?: string; reason?: string }) => Promise<void>;
}

const actionCopy: Record<LicenseDialogAction, { title: string; submit: string; token: boolean }> = {
  ACTIVATE: { title: "Activate License", submit: "Activate License", token: true },
  SUSPEND: { title: "Suspend License", submit: "Suspend License", token: true },
  REACTIVATE: { title: "Reactivate License", submit: "Reactivate License", token: true },
  UPGRADE: { title: "Upgrade License", submit: "Upgrade License", token: true },
};

export default function LicenseActionDialog({
  action,
  isSubmitting,
  onClose,
  onSubmit,
}: LicenseActionDialogProps) {
  const [token, setToken] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  if (!action) return null;
  const copy = actionCopy[action];

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (copy.token && !token.trim()) {
      setError("Encrypted license token is required.");
      return;
    }
    setError("");
    await onSubmit({
      encryptedLicenseToken: copy.token ? token.trim() : undefined,
      reason: reason.trim() || undefined,
    });
  };

  return (
    <Dialog
      isOpen
      title={copy.title}
      onClose={onClose}
      widthClassName="max-w-[620px]"
      contentClassName="p-5"
    >
      <form className="grid gap-5" onSubmit={submit}>
        {copy.token ? (
          <label className="grid gap-2">
            <span className="type-filter-label text-text-heading">
              Encrypted License Token <span className="text-danger">*</span>
            </span>
            <textarea
              value={token}
              onChange={(event) => setToken(event.target.value)}
              rows={6}
              placeholder="Paste the encrypted license token"
              className="module-glass-control type-filter-value min-h-[132px] resize-y rounded-[4px] px-3 py-2.5 text-text-body outline-none focus:border-primary"
            />
            {error ? <span className="text-[10px] text-danger">{error}</span> : null}
          </label>
        ) : null}

        <label className="grid gap-2">
          <span className="type-filter-label text-text-heading">Reason</span>
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            placeholder="Enter the reason for this action"
            className="module-glass-control type-filter-value resize-none rounded-[4px] px-3 py-2.5 text-text-body outline-none focus:border-primary"
          />
        </label>

        <div className="flex justify-end gap-3 border-t border-line/70 pt-4">
          <Button variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" size="sm" isLoading={isSubmitting} rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]">
            {copy.submit}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
