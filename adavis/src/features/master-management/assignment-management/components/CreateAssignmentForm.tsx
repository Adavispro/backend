"use client";

import Image from "next/image";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useMasterLookups } from "../../lookups/hooks";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { createAssignment } from "../api";
import type { CreateAssignmentValues } from "../api";
import { createAssignmentSchema } from "../schemas";
import AssignmentFormFields, { type AssignmentField } from "./AssignmentFormFields";

const emptyValues = (): CreateAssignmentValues => ({
  assignmentType: "GROUP_SCOPE",
  tenantId: "",
  userId: "",
  groupId: "",
  scopeType: "PLANT",
  plantId: "",
  resourceId: "",
  assignedBy: "",
  reason: "",
});

export default function CreateAssignmentForm() {
  const router = useRouter();
  const context = useLoginContext();
  const { options, isLoading: lookupsLoading } = useMasterLookups();
  const { tenants, isLoading: tenantsLoading } = useTenants();
  const { data: topology, isLoading: topologyLoading } = usePlantTopology();
  const [values, setValues] = useState<CreateAssignmentValues>(emptyValues);
  const [errors, setErrors] = useState<Partial<Record<AssignmentField, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");

  const change = (field: AssignmentField, value: string) => {
    setValues((current) => ({
      ...current,
      [field]: value,
      ...(field === "scopeType" ? (value === "PLANT" ? { resourceId: "" } : { plantId: "" }) : {}),
      ...(field === "tenantId" ? { plantId: "" } : {}),
    } as CreateAssignmentValues));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const candidate = { ...values, assignedBy: context?.user.userId ?? values.assignedBy };
    const parsed = createAssignmentSchema.safeParse(candidate);
    if (!parsed.success) { setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message]))); return; }
    setIsSubmitting(true);
    try { await createAssignment(parsed.data); router.push(ROUTES.masterAssignments); router.refresh(); }
    catch (error) { setMessage(error instanceof ApiError ? error.message : "Unable to create assignment."); }
    finally { setIsSubmitting(false); }
  };
  const plants = topology.plants.filter((plant) => plant.isActive && (!values.tenantId || plant.tenantId === values.tenantId)).map((plant) => ({ value: plant.plantId, label: `${plant.plantName} (${plant.plantId})` }));
  const tenantOptions = tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` }));

  return <form onSubmit={submit} className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]"><div className="border-b border-[#E3E9F0] px-6 py-5"><h2 className="text-[14px] font-semibold text-text-heading">Enter Assignment Details</h2><p className="mt-2 text-[9px] text-text-secondary">Grant or exclude a user group assignment for an MDM or IIOT resource.</p></div><div className="grid gap-x-10 gap-y-6 px-6 py-5 md:grid-cols-2 xl:grid-cols-3"><AssignmentFormFields values={values} errors={errors} tenantOptions={tenantOptions} userOptions={options.users} groupOptions={options.groups} plantOptions={plants} onChange={change} /><div className="flex justify-end gap-3 md:col-span-2 xl:col-span-3"><Button type="button" variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary" prefixIcon={<Image src={clearIcon} alt="" className="h-3.5 w-3.5" />} onClick={() => { setValues(emptyValues()); setErrors({}); }}>Clear All</Button><Button type="submit" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9" isLoading={isSubmitting || lookupsLoading || tenantsLoading || topologyLoading}>Create Assignment</Button></div></div><Snackbar open={Boolean(message)} title="Unable to create assignment" message={message} variant="error" onClose={() => setMessage("")} /></form>;
}
