"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { useMemo, useState } from "react";
import { CaretDown, Check } from "@phosphor-icons/react/dist/ssr";
import { Dialog, MultiSelectDropdown, TextField } from "@/components/ui";
import idIcon from "@/assets/icons/id-icon.svg";
import roleIcon from "@/assets/icons/roleName.svg";
import userIcon from "@/assets/icons/user.svg";
import userGroupIcon from "@/assets/icons/userGroup.svg";
import type { MasterLookupOption } from "../../lookups/api";
import { getPermissionMatrix, getRolePermissions } from "../../role-management/api";
import type { RolePermission } from "../../role-management/api/types";

export type UserGroupFormFieldId =
  | "tenantId"
  | "groupCode"
  | "name"
  | "description"
  | "assignedUsers"
  | "assignedRoles";

export type UserGroupFormValues = {
  tenantId: string;
  groupCode: string;
  name: string;
  description: string;
  assignedUsers: string[];
  assignedRoles: string[];
};

interface UserGroupFieldConfig {
  id: UserGroupFormFieldId;
  label: string;
  placeholder: string;
  icon: StaticImageData;
  options?: MasterLookupOption[];
  required?: boolean;
  textarea?: boolean;
  readOnly?: boolean;
  multiple?: boolean;
  colSpanClassName?: string;
}

export const createEmptyUserGroupFormValues = (): UserGroupFormValues => ({
  tenantId: "",
  groupCode: "",
  name: "",
  description: "",
  assignedUsers: [],
  assignedRoles: [],
});

const emptySelectOption = (label: string): MasterLookupOption => ({
  label,
  value: "",
});

export const createUserGroupFields = ({
  roles = [],
  tenants = [],
  users = [],
}: {
  roles?: MasterLookupOption[];
  tenants?: MasterLookupOption[];
  users?: MasterLookupOption[];
} = {}): UserGroupFieldConfig[] => [
  {
    id: "tenantId",
    label: "Tenant",
    placeholder: "Select Tenant",
    icon: idIcon,
    options: [emptySelectOption("Select Tenant"), ...tenants],
    required: true,
  },
  {
    id: "groupCode",
    label: "Group Code",
    placeholder: "Enter Group Code",
    icon: idIcon,
    required: true,
  },
  {
    id: "name",
    label: "User Group Name",
    placeholder: "Enter User Group Name",
    icon: userGroupIcon,
    required: true,
  },
  {
    id: "description",
    label: "Description",
    placeholder: "Enter Group Description",
    icon: userGroupIcon,
    textarea: true,
    colSpanClassName: "md:col-span-2",
  },
  {
    id: "assignedUsers",
    label: "Assign Users",
    placeholder: "Select Users",
    icon: userIcon,
    options: users,
    multiple: true,
  },
  {
    id: "assignedRoles",
    label: "Assign Roles",
    placeholder: "Select Roles",
    icon: roleIcon,
    options: roles,
    multiple: true,
  },
];

export const userGroupFields = createUserGroupFields();

export const createEditUserGroupFields = (options?: {
  roles?: MasterLookupOption[];
  tenants?: MasterLookupOption[];
  users?: MasterLookupOption[];
}) => createUserGroupFields(options);

export const editUserGroupFields = createEditUserGroupFields();

const permissionActions = ["READ", "WRITE", "MODIFY", "APPROVE", "DEACTIVATE"] as const;

function ReadOnlyPermissionToggle({ checked, label }: { checked: boolean; label: string }) {
  return (
    <span className="inline-flex h-8 min-w-[76px] items-center justify-center gap-2 rounded-[4px] border border-[#D6E1ED] bg-white/45 px-3 text-[9px] font-medium text-text-secondary">
      <span
        aria-hidden="true"
        className={`grid h-3.5 w-3.5 place-items-center rounded-[2px] border ${checked ? "border-primary bg-primary text-white" : "border-[#C8D4E1] bg-white"}`}
      >
        {checked ? <Check size={9} weight="bold" /> : null}
      </span>
      <span className="leading-none">{label}</span>
    </span>
  );
}

export function FieldLabel({
  icon,
  required,
  children,
}: {
  icon: StaticImageData;
  required?: boolean;
  children: string;
}) {
  return (
    <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
      <Image src={icon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
      <span>
        {children}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </span>
    </span>
  );
}

export function UserGroupFormFields({
  fields = userGroupFields,
  values,
  errors,
  onChange,
}: {
  fields?: UserGroupFieldConfig[];
  values: UserGroupFormValues;
  errors: Partial<Record<UserGroupFormFieldId, string>>;
  onChange: (field: UserGroupFormFieldId, value: string | string[]) => void;
}) {
  const [isRolePreviewOpen, setIsRolePreviewOpen] = useState(false);
  const [isRolePreviewLoading, setIsRolePreviewLoading] = useState(false);
  const [rolePreviewError, setRolePreviewError] = useState("");
  const [rolePermissionsByRoleId, setRolePermissionsByRoleId] = useState<Record<string, RolePermission[]>>({});
  const [moduleNamesById, setModuleNamesById] = useState<Record<string, string>>({});
  const [screenNamesById, setScreenNamesById] = useState<Record<string, string>>({});
  const [featureNamesById, setFeatureNamesById] = useState<Record<string, string>>({});

  const roleField = useMemo(
    () => fields.find((field) => field.id === "assignedRoles"),
    [fields],
  );
  const roleOptions = roleField?.options ?? [];
  const roleLabelsById = useMemo(
    () => Object.fromEntries(roleOptions.map((option) => [option.value, option.label || option.value])),
    [roleOptions],
  );
  const selectedRoleIds = values.assignedRoles ?? [];
  const selectedRoleLabels = selectedRoleIds.map((roleId) => roleLabelsById[roleId] || roleId);

  const openRolePreview = async () => {
    setIsRolePreviewOpen(true);
    setRolePreviewError("");

    if (selectedRoleIds.length === 0) {
      setRolePermissionsByRoleId({});
      return;
    }

    setIsRolePreviewLoading(true);
    try {
      const [permissionMatrix, rolePermissionEntries] = await Promise.all([
        getPermissionMatrix(),
        Promise.all(
          selectedRoleIds.map(async (roleId) => [roleId, await getRolePermissions(roleId)] as const),
        ),
      ]);

      setRolePermissionsByRoleId(Object.fromEntries(rolePermissionEntries));
      setModuleNamesById(Object.fromEntries(permissionMatrix.modules.map((module) => [module.moduleId, module.moduleName])));
      setScreenNamesById(
        Object.fromEntries(
          permissionMatrix.modules.flatMap((module) => module.screens.map((screen) => [screen.screenId, screen.screenName] as const)),
        ),
      );
      setFeatureNamesById(
        Object.fromEntries(
          permissionMatrix.modules.flatMap((module) =>
            module.screens.flatMap((screen) => screen.features.map((feature) => [feature.featureId, feature.featureName] as const)),
          ),
        ),
      );
    } catch (error) {
      setRolePreviewError(error instanceof Error ? error.message : "Unable to load role permissions.");
    } finally {
      setIsRolePreviewLoading(false);
    }
  };

  return (
    <>
      {fields.map((field) => (
        <div
          key={field.id}
          className={`grid gap-2 ${field.colSpanClassName ?? ""}`}
        >
          <FieldLabel icon={field.icon} required={field.required}>
            {field.label}
          </FieldLabel>
          {field.options && field.multiple ? (
            <>
              <MultiSelectDropdown
                id={field.id}
                disabled={field.readOnly}
                options={field.options}
                placeholder={field.placeholder}
                selectedValues={values[field.id] as string[]}
                onChange={(nextValues) => onChange(field.id, nextValues)}
              />

              {field.id === "assignedRoles" ? (
                <div className="grid gap-2">
                  {selectedRoleLabels.length > 0 ? (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {selectedRoleLabels.map((roleLabel) => (
                        <span
                          key={roleLabel}
                          className="inline-flex h-6 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-medium text-text-secondary"
                          title={roleLabel}
                        >
                          {roleLabel}
                        </span>
                      ))}
                    </div>
                  ) : null}

                  <div className="flex justify-start">
                    <button
                      type="button"
                      onClick={() => void openRolePreview()}
                      className="inline-flex h-8 items-center rounded-[4px] border border-[#C8D7E8] bg-white px-3 text-[10px] font-semibold text-primary transition-colors hover:bg-[#F4F9FF]"
                    >
                      Preview Role Permissions
                    </button>
                  </div>
                </div>
              ) : null}

              {errors[field.id] ? (
                <span className="text-[9px] text-danger">
                  {errors[field.id]}
                </span>
              ) : null}
            </>
          ) : field.options ? (
            <>
              <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
                <select
                  id={field.id}
                  value={values[field.id] as string}
                  disabled={field.readOnly}
                  onChange={(event) => onChange(field.id, event.target.value)}
                  className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {field.options.map((option) => (
                    <option key={`${field.id}-${option.value}-${option.label}`} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                <CaretDown
                  aria-hidden="true"
                  size={11}
                  weight="bold"
                  className="pointer-events-none absolute right-3 text-text-secondary"
                />
              </span>
              {errors[field.id] ? (
                <span className="text-[9px] text-danger">
                  {errors[field.id]}
                </span>
              ) : null}
            </>
          ) : field.textarea ? (
            <>
              <span className="module-glass-control flex rounded-[4px] px-3 py-2">
                <textarea
                  id={field.id}
                  rows={4}
                  placeholder={field.placeholder}
                  value={values[field.id] as string}
                  readOnly={field.readOnly}
                  onChange={(event) => onChange(field.id, event.target.value)}
                  className="type-filter-value min-h-[88px] w-full resize-none bg-transparent outline-none placeholder:text-text-secondary read-only:cursor-not-allowed"
                />
              </span>
              {errors[field.id] ? (
                <span className="text-[9px] text-danger">
                  {errors[field.id]}
                </span>
              ) : null}
            </>
          ) : (
            <TextField
              id={field.id}
              placeholder={field.placeholder}
              value={values[field.id] as string}
              readOnly={field.readOnly}
              onChange={(event) => onChange(field.id, event.target.value)}
              error={errors[field.id]}
              containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
              inputClassName="type-filter-value placeholder:text-text-secondary read-only:cursor-not-allowed"
            />
          )}
        </div>
      ))}

      <Dialog
        isOpen={isRolePreviewOpen}
        title="Role Permission Preview"
        onClose={() => {
          setIsRolePreviewOpen(false);
          setRolePreviewError("");
        }}
        widthClassName="max-w-[1180px]"
        contentClassName="max-h-[calc(100vh-90px)] overflow-y-auto p-5"
      >
        <div className="grid gap-4">
          {isRolePreviewLoading ? (
            <p className="text-[10px] text-text-secondary">Loading role permissions...</p>
          ) : rolePreviewError ? (
            <p className="text-[10px] text-danger">{rolePreviewError}</p>
          ) : selectedRoleIds.length === 0 ? (
            <p className="text-[10px] text-text-secondary">No roles selected.</p>
          ) : (
            selectedRoleIds.map((roleId) => {
              const roleLabel = roleLabelsById[roleId] || roleId;
              const permissions = rolePermissionsByRoleId[roleId] ?? [];
              const activeModulePermissions = permissions.filter((modulePermission) => modulePermission.isActive && modulePermission.screenPermissions.length > 0);

              return (
                <div key={roleId} className="rounded-[8px] border border-[#DCE6F2] bg-[#F9FCFF] p-4">
                  <p className="text-[10px] font-semibold text-primary">{roleLabel}</p>
                  {activeModulePermissions.length > 0 ? (
                    <div className="mt-2 grid gap-2">
                      {activeModulePermissions.map((modulePermission) => (
                        <div key={`${roleId}-${modulePermission.moduleId}`} className="rounded-[4px] bg-[#F7FAFE] px-3 py-2">
                          <p className="text-[9px] font-semibold text-text-heading">{moduleNamesById[modulePermission.moduleId] || modulePermission.moduleId}</p>
                          <div className="mt-1 grid gap-2">
                            {modulePermission.screenPermissions.map((screenPermission) => (
                              <div key={`${roleId}-${modulePermission.moduleId}-${screenPermission.screenId}`} className="rounded-[4px] border border-[#E3E9F0] bg-white/70 p-2 text-[8px] text-text-secondary">
                                <p className="font-semibold text-text-heading">{screenNamesById[screenPermission.screenId] || screenPermission.screenId}</p>
                                <div className="mt-1 flex flex-wrap gap-2">
                                  {permissionActions.map((action) => (
                                    <ReadOnlyPermissionToggle
                                      key={`${roleId}-${screenPermission.screenId}-${action}`}
                                      label={action.charAt(0) + action.slice(1).toLowerCase()}
                                      checked={screenPermission.actions.includes(action)}
                                    />
                                  ))}
                                </div>
                                {screenPermission.featurePermissions.length > 0 ? (
                                  <div className="mt-2 ml-2 grid gap-1">
                                    {screenPermission.featurePermissions.map((featurePermission) => (
                                      <div key={`${roleId}-${featurePermission.featureId}`}>
                                        <p className="text-[8px] font-semibold text-text-heading">{featureNamesById[featurePermission.featureId] || featurePermission.featureId}</p>
                                        <div className="mt-1 flex flex-wrap gap-2">
                                          {permissionActions.map((action) => (
                                            <ReadOnlyPermissionToggle
                                              key={`${roleId}-${featurePermission.featureId}-${action}`}
                                              label={action.charAt(0) + action.slice(1).toLowerCase()}
                                              checked={featurePermission.actions.includes(action)}
                                            />
                                          ))}
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                ) : null}
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="mt-2 text-[9px] text-text-secondary">No active permissions assigned.</p>
                  )}
                </div>
              );
            })
          )}
        </div>
      </Dialog>
    </>
  );
}
