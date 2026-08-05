"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { useMemo, useState } from "react";
import { CaretDown, Check, Eye, EyeSlash } from "@phosphor-icons/react/dist/ssr";
import { Dialog, MultiSelectDropdown, TextField } from "@/components/ui";
import divisionIcon from "@/assets/icons/division.svg";
import emailIcon from "@/assets/icons/email.svg";
import idIcon from "@/assets/icons/id-icon.svg";
import passwordIcon from "@/assets/icons/password.svg";
import phoneIcon from "@/assets/icons/phone.svg";
import plantIcon from "@/assets/icons/plant-icon.svg";
import rolesIcon from "@/assets/icons/roleName.svg";
import userIcon from "@/assets/icons/user.svg";
import groupIcon from "@/assets/icons/userGroup.svg";
import type { MasterLookupOption } from "../../lookups/api";
import { getPermissionMatrix, getRolePermissions } from "../../role-management/api";
import type { RolePermission } from "../../role-management/api/types";
import { getGroupAssignments } from "../../user-group-management/api";

export type UserFormFieldId =
  | "userId"
  | "tenantId"
  | "username"
  | "department"
  | "title"
  | "userType"
  | "lifecycleStatus"
  | "empId"
  | "reason"
  | "firstName"
  | "lastName"
  | "email"
  | "phone"
  | "password"
  | "confirmPassword"
  | "userGroup"
  | "status";

export type UserFormValues = {
  userId: string;
  tenantId: string;
  username: string;
  department: string;
  title: string;
  userType: string;
  lifecycleStatus: string;
  empId: string;
  reason: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  userGroup: string[];
  status: string;
};

export interface UserFormFieldConfig {
  id: UserFormFieldId;
  label: string;
  placeholder: string;
  icon: StaticImageData;
  options?: MasterLookupOption[];
  type?: "text" | "email" | "tel" | "password";
  readOnly?: boolean;
  required?: boolean;
}

export const createEmptyUserFormValues = (): UserFormValues => ({
  userId: "",
  tenantId: "",
  username: "",
  department: "",
  title: "",
  userType: "INTERNAL_EMPLOYEE",
  lifecycleStatus: "ACTIVE",
  empId: "",
  reason: "",
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  password: "",
  confirmPassword: "",
  userGroup: [],
  status: "",
});

const emptySelectOption = (label: string): MasterLookupOption => ({
  label,
  value: "",
});

export const createUserInformationFields = ({
  departments = [],
  tenants = [],
}: {
  departments?: MasterLookupOption[];
  tenants?: MasterLookupOption[];
} = {}): UserFormFieldConfig[] => [
  {
    id: "tenantId",
    label: "Tenant",
    placeholder: "Select Tenant",
    icon: plantIcon,
    options: [emptySelectOption("Select Tenant"), ...tenants],
    required: true,
  },
  {
    id: "department",
    label: "Department",
    placeholder: "Select Department",
    icon: plantIcon,
    options: [emptySelectOption("Select Department"), ...departments],
    required: true,
  },
  {
    id: "title",
    label: "Title",
    placeholder: "Enter Job Title",
    icon: divisionIcon,
    required: true,
  },
  {
    id: "empId",
    label: "Employee ID",
    placeholder: "Enter Employee ID",
    icon: idIcon,
    required: true,
  },
  {
    id: "userType",
    label: "User Type",
    placeholder: "Select User Type",
    icon: divisionIcon,
    options: [
      { label: "Internal Employee", value: "INTERNAL_EMPLOYEE" },
      { label: "External User", value: "EXTERNAL_USER" },
    ],
    required: true,
  },
  {
    id: "lifecycleStatus",
    label: "Lifecycle Status",
    placeholder: "Select Lifecycle Status",
    icon: rolesIcon,
    options: [
      { label: "Active", value: "ACTIVE" },
      { label: "Inactive", value: "INACTIVE" },
    ],
    required: true,
  },
  {
    id: "reason",
    label: "Onboarding Reason",
    placeholder: "Enter onboarding reason",
    icon: groupIcon,
    required: true,
  },
];

export const userInformationFields = createUserInformationFields();

export const passwordFields: UserFormFieldConfig[] = [
  {
    id: "password",
    label: "Password",
    placeholder: "Enter Password",
    icon: passwordIcon,
    type: "password",
    required: true,
  },
  {
    id: "confirmPassword",
    label: "Confirm Password",
    placeholder: "Confirm Password",
    icon: passwordIcon,
    type: "password",
    required: true,
  },
];

export const accountFields: UserFormFieldConfig[] = [
  {
    id: "username",
    label: "Username",
    placeholder: "Enter Username",
    icon: userIcon,
    required: true,
  },
  {
    id: "firstName",
    label: "First Name",
    placeholder: "Enter First Name",
    icon: userIcon,
    required: true,
  },
  {
    id: "lastName",
    label: "Last Name",
    placeholder: "Enter Last Name",
    icon: userIcon,
    required: true,
  },
  {
    id: "email",
    label: "Email Address",
    placeholder: "Enter Email Address",
    icon: emailIcon,
    type: "email",
    required: true,
  },
  {
    id: "phone",
    label: "Phone Number",
    placeholder: "Enter Phone Number",
    icon: phoneIcon,
    type: "tel",
  },
  ...passwordFields,
];

export const userStatusField: UserFormFieldConfig = {
  id: "status",
  label: "User Status",
  placeholder: "Select User Status",
  icon: rolesIcon,
  options: [
    emptySelectOption("Select User Status"),
    { label: "Active", value: "Active" },
    { label: "Blocked", value: "Blocked" },
    { label: "Deactivated", value: "Deactivated" },
  ],
};

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

export function UserFormField({
  field,
  value,
  error,
  onChange,
}: {
  field: UserFormFieldConfig;
  value: string;
  error?: string;
  onChange: (field: UserFormFieldId, value: string) => void;
}) {
  const { id, label, placeholder, icon, options, readOnly, required, type = "text" } =
    field;
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const isPasswordField = type === "password";

  return (
    <label htmlFor={id} className="grid gap-2">
      <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
        <Image src={icon} alt="" aria-hidden="true" className="h-3 w-3" />
        {label}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </span>

      {options ? (
        <>
          <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
            <select
              id={id}
              value={value}
              disabled={readOnly}
              onChange={(event) => onChange(id, event.target.value)}
              className="module-glass-select type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed disabled:opacity-70"
            >
              {options.map((option) => (
                <option
                  key={`${id}-${option.value}-${option.label}`}
                  value={option.value}
                  className="bg-white text-text-heading"
                >
                  {option.label || option.value}
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
          {error ? <span className="text-[9px] text-danger">{error}</span> : null}
        </>
      ) : (
        <TextField
          id={id}
          type={isPasswordField && isPasswordVisible ? "text" : type}
          placeholder={placeholder}
          value={value}
          readOnly={readOnly}
          required={required}
          suffixIcon={
            isPasswordField ? (
              isPasswordVisible ? (
                <EyeSlash size={15} weight="bold" />
              ) : (
                <Eye size={15} weight="bold" />
              )
            ) : undefined
          }
          suffixIconLabel={
            isPasswordField
              ? isPasswordVisible
                ? "Hide password"
                : "Show password"
              : undefined
          }
          onSuffixIconClick={
            isPasswordField
              ? () => setIsPasswordVisible((previous) => !previous)
              : undefined
          }
          onChange={(event) => onChange(id, event.target.value)}
          error={error}
          containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
          inputClassName="type-filter-value placeholder:text-text-secondary read-only:cursor-not-allowed"
          hintClassName="text-[9px]"
        />
      )}
    </label>
  );
}

export function UserFormSection({
  title,
  subtitle,
  fields,
  values,
  errors,
  onChange,
  columnsClassName = "md:grid-cols-2 xl:grid-cols-3",
  compact = false,
}: {
  title: string;
  subtitle?: string;
  fields: UserFormFieldConfig[];
  values: UserFormValues;
  errors: Partial<Record<UserFormFieldId, string>>;
  onChange: (field: UserFormFieldId, value: string) => void;
  columnsClassName?: string;
  compact?: boolean;
}) {
  return (
    <section className={compact ? "grid gap-4" : "module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]"}>
      <div className={compact ? "" : "border-b border-[#E3E9F0] px-6 py-5"}>
        <h2 className="text-[14px] font-semibold text-text-heading">{title}</h2>
        {subtitle ? (
          <p className="mt-2 text-[9px] text-text-secondary">{subtitle}</p>
        ) : null}
      </div>
      <div className={`grid gap-x-10 gap-y-6 ${compact ? "" : "px-6 py-5"} ${columnsClassName}`}>
        {fields.map((field) => (
          <UserFormField
            key={field.id}
            field={field}
            value={String(values[field.id] ?? "")}
            error={errors[field.id]}
            onChange={onChange}
          />
        ))}
      </div>
    </section>
  );
}

export function UserStepIndicator({
  activeStep,
  onStepChange,
}: {
  activeStep: 1 | 2;
  onStepChange?: (step: 1 | 2) => void;
}) {
  const stepClassName = (step: 1 | 2) =>
    `module-glass-control flex h-10 min-w-[190px] items-center gap-3 rounded-lg px-5 text-[10px] font-semibold text-primary transition-colors ${
      activeStep === step ? "border-primary/35 bg-white/70" : "hover:border-primary/30 hover:bg-white/55"
    } ${onStepChange ? "cursor-pointer" : ""}`;

  return (
    <div className="flex max-w-[520px] items-center">
      <button
        type="button"
        onClick={() => onStepChange?.(1)}
        className={stepClassName(1)}
        aria-current={activeStep === 1 ? "step" : undefined}
      >
        <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-[9px] text-white">
          {activeStep === 2 ? <Check size={11} weight="bold" /> : "1"}
        </span>
        User Creation
      </button>
      <span className="h-px flex-1 border-t border-dashed border-[#9DB0C5]" />
      <button
        type="button"
        onClick={() => onStepChange?.(2)}
        className={stepClassName(2)}
        aria-current={activeStep === 2 ? "step" : undefined}
      >
        <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-[9px] text-white">2</span>
        Assign User Group
      </button>
    </div>
  );
}

export function AssignmentSummaryCard({
  icon,
  label,
  value,
}: {
  icon: StaticImageData;
  label: string;
  value: string;
}) {
  return (
    <div className="flex min-h-[64px] min-w-[230px] items-start gap-3 rounded-lg border border-[#D8E6F6] bg-[#EAF3FF]/80 px-4 py-3">
      <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-[#D7E9FF]">
        <Image src={icon} alt="" aria-hidden="true" className="h-4 w-4 object-contain" />
      </span>
      <div>
        <p className="text-[9px] font-semibold text-text-heading">{label}</p>
        <p className="mt-1 text-[9px] font-medium text-text-heading">{value}</p>
      </div>
    </div>
  );
}

export function AssignUserGroupFields({
  options = [],
  roleOptions = [],
  value,
  onChange,
}: {
  options?: MasterLookupOption[];
  roleOptions?: MasterLookupOption[];
  value: string[];
  onChange: (value: string[]) => void;
}) {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [groupRoleIdsById, setGroupRoleIdsById] = useState<Record<string, string[]>>({});
  const [rolePermissionsByRoleId, setRolePermissionsByRoleId] = useState<Record<string, RolePermission[]>>({});
  const [moduleNamesById, setModuleNamesById] = useState<Record<string, string>>({});
  const [screenNamesById, setScreenNamesById] = useState<Record<string, string>>({});
  const [featureNamesById, setFeatureNamesById] = useState<Record<string, string>>({});
  const selectedGroups = options.filter((option) => value.includes(option.value));
  const roleLabelsById = useMemo(
    () => Object.fromEntries(roleOptions.map((option) => [option.value, option.label || option.value])),
    [roleOptions],
  );
  const groupLabel =
    selectedGroups.length === 0
      ? "-"
      : selectedGroups.length <= 2
        ? selectedGroups.map((option) => option.label || option.value).join(", ")
        : `${selectedGroups.length} groups selected`;

  const openPreview = async () => {
    setIsPreviewOpen(true);
    setPreviewError("");

    if (selectedGroups.length === 0) {
      setGroupRoleIdsById({});
      setRolePermissionsByRoleId({});
      return;
    }

    setIsPreviewLoading(true);
    try {
      const [permissionMatrix, assignmentEntries] = await Promise.all([
        getPermissionMatrix(),
        Promise.all(
          selectedGroups.map(async (group) => {
            const assignment = await getGroupAssignments(group.value);
            return [group.value, assignment.roleIds] as const;
          }),
        ),
      ]);

      const uniqueRoleIds = Array.from(new Set(assignmentEntries.flatMap(([, roleIds]) => roleIds))).filter(Boolean);
      const rolePermissionEntries = await Promise.all(
        uniqueRoleIds.map(async (roleId) => [roleId, await getRolePermissions(roleId)] as const),
      );

      setGroupRoleIdsById(Object.fromEntries(assignmentEntries));
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
      setPreviewError(error instanceof Error ? error.message : "Unable to load role assignments for selected groups.");
    } finally {
      setIsPreviewLoading(false);
    }
  };

  return (
    <>
      <label htmlFor="userGroup" className="grid gap-2">
        <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
          <Image src={groupIcon} alt="" aria-hidden="true" className="h-3 w-3" />
          User Group
        </span>
        <MultiSelectDropdown
          id="userGroup"
          options={options}
          placeholder="Select User Group"
          selectedValues={value}
          onChange={onChange}
        />
      </label>

      {selectedGroups.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {selectedGroups.map((group) => (
            <span
              key={group.value}
              className="inline-flex h-6 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-medium text-text-secondary"
              title={group.label || group.value}
            >
              {group.label || group.value}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-4 flex justify-end">
        <button
          type="button"
          onClick={() => void openPreview()}
          className="inline-flex h-8 items-center rounded-[4px] border border-[#C8D7E8] bg-white px-3 text-[10px] font-semibold text-primary transition-colors hover:bg-[#F4F9FF]"
        >
          Preview Group Roles
        </button>
      </div>

      <div className="mt-5 flex flex-wrap gap-4">
        <AssignmentSummaryCard
          icon={groupIcon}
          label="Group Name"
          value={groupLabel}
        />
        <AssignmentSummaryCard
          icon={rolesIcon}
          label="Assigned Roles"
          value={selectedGroups.length === 0 ? "-" : "Use preview to view assigned roles"}
        />
      </div>

      <Dialog
        isOpen={isPreviewOpen}
        title="Selected Group Role Preview"
        onClose={() => {
          setIsPreviewOpen(false);
          setPreviewError("");
        }}
        widthClassName="max-w-[1180px]"
        contentClassName="max-h-[calc(100vh-90px)] overflow-y-auto p-5"
      >
        <div className="grid gap-4">
          {isPreviewLoading ? (
            <p className="text-[10px] text-text-secondary">Loading group role assignments...</p>
          ) : previewError ? (
            <p className="text-[10px] text-danger">{previewError}</p>
          ) : selectedGroups.length === 0 ? (
            <p className="text-[10px] text-text-secondary">No groups selected.</p>
          ) : (
            selectedGroups.map((group) => {
              const roleIds = groupRoleIdsById[group.value] ?? [];
              return (
                <div key={group.value} className="rounded-[8px] border border-[#DCE6F2] bg-[#F9FCFF] p-4">
                  <p className="text-[10px] font-semibold text-text-heading">{group.label || group.value}</p>
                  <div className="mt-3 grid gap-3">
                    {roleIds.length > 0 ? (
                      roleIds.map((roleId) => {
                        const roleLabel = roleLabelsById[roleId] || roleId;
                        const permissions = rolePermissionsByRoleId[roleId] ?? [];
                        const activeModulePermissions = permissions.filter((modulePermission) => modulePermission.isActive && modulePermission.screenPermissions.length > 0);

                        return (
                          <div key={`${group.value}-${roleId}`} className="rounded-[6px] border border-[#E2EAF3] bg-white p-3">
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
                    ) : (
                      <span className="text-[9px] text-text-secondary">No roles assigned.</span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </Dialog>
    </>
  );
}
