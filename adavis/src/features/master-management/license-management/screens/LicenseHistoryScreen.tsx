import { Suspense } from "react";
import LicenseHistoryTable from "../components/LicenseHistoryTable";

export default function LicenseHistoryScreen() {
  return <Suspense fallback={<div className="module-glass-panel min-h-[420px] rounded-xl p-5 text-xs text-text-secondary">Loading license history...</div>}><LicenseHistoryTable /></Suspense>;
}
