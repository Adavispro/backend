import { Suspense } from "react";
import PlantTopologyWorkspace from "../components/PlantTopologyWorkspace";

export default function PlantTopologyScreen() {
  return <Suspense fallback={<div className="module-glass-panel min-h-[420px] rounded-xl p-5 text-xs text-text-secondary">Loading plant topology...</div>}><PlantTopologyWorkspace /></Suspense>;
}
