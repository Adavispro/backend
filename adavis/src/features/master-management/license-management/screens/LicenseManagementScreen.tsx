import { Suspense } from "react";
import LicenseOverview from "../components/LicenseOverview";

export default function LicenseManagementScreen() {
  return <Suspense fallback={<div className="module-glass-panel min-h-[420px] rounded-xl p-5 text-xs text-text-secondary">Loading license details...</div>}><LicenseOverview /></Suspense>;
}
