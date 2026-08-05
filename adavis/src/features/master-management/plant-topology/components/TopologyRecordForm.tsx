"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import { CaretDown } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Button, Snackbar, TextField } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import type { Tenant } from "../../tenant-management/api";
import { createTopologyRecord, updateTopologyRecord } from "../api";
import type { TopologyKind, TopologyRecord, TopologyRequest } from "../api";
import type { TopologyData } from "../hooks/usePlantTopology";
import { areaRequestSchema, blockRequestSchema, plantRequestSchema, roomRequestSchema } from "../schemas";

type FormValues = Record<string, string>;

export const topologyLabels: Record<TopologyKind, string> = {
  plants: "Plant",
  blocks: "Block",
  areas: "Area",
  rooms: "Room",
};

const schemas = { plants: plantRequestSchema, blocks: blockRequestSchema, areas: areaRequestSchema, rooms: roomRequestSchema };

const initialValues = (kind: TopologyKind, tenantId: string, record?: TopologyRecord | null): FormValues => {
  const value = record as unknown as Record<string, unknown> | undefined;
  return {
    tenantId: String(value?.tenantId ?? tenantId),
    plantId: String(value?.plantId ?? ""),
    blockId: String(value?.blockId ?? ""),
    areaId: String(value?.areaId ?? ""),
    code: String(value?.[`${kind.slice(0, -1)}Code`] ?? ""),
    name: String(value?.[`${kind.slice(0, -1)}Name`] ?? ""),
    type: String(value?.type ?? "Manufacturing"),
    classification: String(value?.classification ?? "ISO_7"),
  };
};

export default function TopologyRecordForm({
  kind,
  record = null,
  tenantId,
  tenants,
  topology,
  onCancel,
  onSaved,
}: {
  kind: TopologyKind;
  record?: TopologyRecord | null;
  tenantId: string;
  tenants: Tenant[];
  topology: TopologyData;
  onCancel: () => void;
  onSaved: (record: TopologyRecord) => void;
}) {
  const [values, setValues] = useState<FormValues>(() => initialValues(kind, tenantId, record));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const plants = useMemo(() => topology.plants.filter((plant) => plant.isActive && plant.tenantId === values.tenantId), [topology.plants, values.tenantId]);
  const blocks = useMemo(() => topology.blocks.filter((block) => block.isActive && block.tenantId === values.tenantId && (!values.plantId || block.plantId === values.plantId)), [topology.blocks, values.plantId, values.tenantId]);
  const areas = useMemo(() => topology.areas.filter((area) => area.isActive && area.tenantId === values.tenantId && (!values.plantId || area.plantId === values.plantId)), [topology.areas, values.plantId, values.tenantId]);

  const change = (field: string, value: string) => {
    setValues((current) => {
      const next = { ...current, [field]: value };
      if (field === "tenantId") Object.assign(next, { plantId: "", blockId: "", areaId: "" });
      if (field === "plantId") Object.assign(next, { blockId: "", areaId: "" });
      return next;
    });
    setErrors((current) => ({ ...current, [field]: "" }));
  };

  const buildPayload = () => {
    const common = { tenantId: values.tenantId, isActive: record?.isActive ?? true };
    if (kind === "plants") return { ...common, plantCode: values.code, plantName: values.name, type: values.type, timezone: "Asia/Kolkata" };
    if (kind === "blocks") return { ...common, plantId: values.plantId, blockCode: values.code, blockName: values.name, displayOrder: 1 };
    if (kind === "areas") return { ...common, plantId: values.plantId, blockId: values.blockId, areaCode: values.code, areaName: values.name, displayOrder: 1 };
    return { ...common, plantId: values.plantId, areaId: values.areaId, roomCode: values.code, roomName: values.name, classification: values.classification };
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = schemas[kind].safeParse(buildPayload());
    if (!parsed.success) {
      setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
      return;
    }
    setIsSubmitting(true);
    try {
      const saved = record
        ? await updateTopologyRecord(kind, record, parsed.data as TopologyRequest)
        : await createTopologyRecord(kind, parsed.data as TopologyRequest);
      onSaved(saved);
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : `Unable to save ${topologyLabels[kind].toLowerCase()}.`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const clear = () => {
    setValues(initialValues(kind, tenantId, record));
    setErrors({});
  };

  const input = (field: string, label: string, placeholder: string, type = "text") => (
    <label htmlFor={`topology-${field}`} className="grid gap-2">
      <span className="type-filter-label text-text-heading">{label}<span className="ml-0.5 text-danger">*</span></span>
      <TextField id={`topology-${field}`} type={type} value={values[field]} onChange={(event) => change(field, event.target.value)} placeholder={placeholder} error={errors[field]} containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2" inputClassName="type-filter-value placeholder:text-text-secondary" hintClassName="text-[9px]" />
    </label>
  );

  const select = (field: string, label: string, options: Array<{ value: string; label: string }>) => (
    <label htmlFor={`topology-${field}`} className="grid gap-2">
      <span className="type-filter-label text-text-heading">{label}<span className="ml-0.5 text-danger">*</span></span>
      <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
        <select id={`topology-${field}`} value={values[field]} onChange={(event) => change(field, event.target.value)} className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none">
          <option value="">Select {label}</option>
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <CaretDown size={11} weight="bold" className="pointer-events-none absolute right-3 text-text-secondary" />
      </span>
      {errors[field] ? <span className="text-[9px] text-danger">{errors[field]}</span> : null}
    </label>
  );

  return (
    <>
      <form onSubmit={submit} className="grid gap-x-10 gap-y-6 md:grid-cols-2 xl:grid-cols-3">
        {select("tenantId", "Tenant", tenants.filter((item) => item.isActive).map((item) => ({ value: item.tenantId, label: `${item.companyName} (${item.tenantId})` })))}
        {kind !== "plants" ? select("plantId", "Plant", plants.map((item) => ({ value: item.plantId, label: `${item.plantName} (${item.plantId})` }))) : null}
        {kind === "areas" ? select("blockId", "Block", blocks.map((item) => ({ value: item.blockId, label: `${item.blockName} (${item.blockId})` }))) : null}
        {kind === "rooms" ? select("areaId", "Area", areas.map((item) => ({ value: item.areaId, label: `${item.areaName} (${item.areaId})` }))) : null}
        {input("code", `${topologyLabels[kind]} Code`, `Enter ${topologyLabels[kind].toLowerCase()} code`)}
        {input("name", `${topologyLabels[kind]} Name`, `Enter ${topologyLabels[kind].toLowerCase()} name`)}
        {kind === "plants" ? input("type", "Plant Type", "Manufacturing") : null}
        {kind === "rooms" ? input("classification", "Classification", "ISO_7") : null}
        <div className="flex justify-end gap-3 md:col-span-2 xl:col-span-3">
          {!record ? <Button type="button" variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65" prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />} onClick={clear}>Clear All</Button> : <Button type="button" variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" className="h-9 border-primary/35 !text-primary" onClick={onCancel}>Discard Changes</Button>}
          <Button type="submit" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" isLoading={isSubmitting}>{record ? "Save Changes" : `Create ${topologyLabels[kind]}`}</Button>
        </div>
      </form>
      <Snackbar open={Boolean(message)} title={`Unable to save ${topologyLabels[kind]}`} message={message} variant="error" onClose={() => setMessage("")} />
    </>
  );
}
