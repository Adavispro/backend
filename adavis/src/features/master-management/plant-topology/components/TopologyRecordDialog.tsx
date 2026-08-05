"use client";

import { Dialog } from "@/components/ui";
import type { Tenant } from "../../tenant-management/api";
import type { TopologyKind, TopologyRecord } from "../api";
import type { TopologyData } from "../hooks/usePlantTopology";
import TopologyRecordForm, { topologyLabels } from "./TopologyRecordForm";

export default function TopologyRecordDialog({ kind, record, tenantId, tenants, topology, onClose, onSaved }: {
  kind: TopologyKind | null;
  record: TopologyRecord | null;
  tenantId: string;
  tenants: Tenant[];
  topology: TopologyData;
  onClose: () => void;
  onSaved: (record: TopologyRecord) => void;
}) {
  if (!kind || !record) return null;

  return (
    <Dialog isOpen title={`Edit ${topologyLabels[kind]}`} onClose={onClose} widthClassName="max-w-[920px]" contentClassName="p-6">
      <TopologyRecordForm kind={kind} record={record} tenantId={tenantId} tenants={tenants} topology={topology} onCancel={onClose} onSaved={(saved) => { onSaved(saved); onClose(); }} />
    </Dialog>
  );
}
