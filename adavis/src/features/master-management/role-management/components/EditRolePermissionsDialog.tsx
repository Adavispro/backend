"use client";

import { Dialog } from "@/components/ui";
import type { Role } from "../api/types";
import RolePermissionsEditor from "./RolePermissionsEditor";

export default function EditRolePermissionsDialog({ role, onClose }: { role: Role | null; onClose: () => void }) {
  if (!role) return null;
  return <Dialog isOpen title={`Edit Permissions - ${role.roleName || role.name}`} onClose={onClose} widthClassName="max-w-[1180px]" contentClassName="max-h-[calc(100vh-90px)] overflow-y-auto p-5"><RolePermissionsEditor roleId={role.roleId} /></Dialog>;
}
