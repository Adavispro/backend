"use client";

import { useRouter } from "next/navigation";
import { ROUTES } from "@/config/routes";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import type { TopologyKind, TopologyRecord } from "../api";
import TopologyRecordForm, { topologyLabels } from "../components/TopologyRecordForm";
import { usePlantTopology } from "../hooks/usePlantTopology";

export default function CreateTopologyRecordScreen({ kind, tenantId }: { kind: TopologyKind; tenantId: string }) {
  const router = useRouter();
  const { tenants } = useTenants();
  const { data } = usePlantTopology();
  const returnToList = (savedRecord?: TopologyRecord) => {
    const selectedTenantId = savedRecord?.tenantId || tenantId;
    router.push(
      `${ROUTES.masterPlantTopology}?view=${kind}${selectedTenantId ? `&tenantId=${encodeURIComponent(selectedTenantId)}` : ""}`,
    );
  };

  return (
    <section className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
      <div className="border-b border-[#E3E9F0] px-6 py-5">
        <h2 className="text-[14px] font-semibold text-text-heading">Enter {topologyLabels[kind]} Details</h2>
        <p className="mt-2 text-[9px] text-text-secondary">Fill out the required information to create a new {topologyLabels[kind].toLowerCase()}.</p>
      </div>
      <div className="px-6 py-5">
        <TopologyRecordForm kind={kind} tenantId={tenantId} tenants={tenants} topology={data} onCancel={() => returnToList()} onSaved={returnToList} />
      </div>
    </section>
  );
}
