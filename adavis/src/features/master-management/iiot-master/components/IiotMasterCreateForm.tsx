"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { CaretDown } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Button, Snackbar, TextField } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { ROUTES } from "@/config/routes";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import {
  createCriticalParameter,
  createCriticalParameterLimit,
  createIiotAsset,
  createProductMaster,
  type IiotMasterSection,
} from "../api";
import { useIiotMasterData } from "../hooks/useIiotMasterData";
import {
  createCriticalParameterLimitSchema,
  createCriticalParameterSchema,
  createIiotAssetSchema,
  createProductMasterSchema,
} from "../schemas";

type FieldKind = "text" | "number" | "select" | "checkbox";
type Values = Record<string, string | boolean>;
type Field = {
  id: string;
  label: string;
  placeholder?: string;
  kind?: FieldKind;
  options?: Array<{ value: string; label: string }>;
  disabled?: boolean;
};

const parameterTypeOptions: Array<{ value: string; label: string }> = [
  { value: "", label: "Select Parameter Type" },
  { value: "INT", label: "INT" },
  { value: "FLOAT", label: "FLOAT" },
  { value: "BOOLEAN", label: "BOOLEAN" },
  { value: "ENUM", label: "ENUM" },
  { value: "RANGE", label: "RANGE" },
  { value: "STRING", label: "STRING" },
];

const booleanOptions: Array<{ value: string; label: string }> = [
  { value: "", label: "Select Boolean Value" },
  { value: "true", label: "True" },
  { value: "false", label: "False" },
];

const sectionReturnRoutes: Record<IiotMasterSection, string> = {
  equipments: ROUTES.masterIiotEquipments,
  "critical-parameters": ROUTES.masterIiotCriticalParameters,
  "critical-parameter-limits": ROUTES.masterIiotCriticalParameterLimits,
  "product-master": ROUTES.masterIiotProductMaster,
};

const sectionLabels: Record<IiotMasterSection, string> = {
  equipments: "Equipment",
  "critical-parameters": "Critical Parameter",
  "critical-parameter-limits": "Critical Parameter Limit",
  "product-master": "Product Master",
};

const emptyValues = (section: IiotMasterSection): Values => {
  if (section === "equipments") {
    return {
      equipmentCode: "",
      equipmentName: "",
      tenantId: "",
      plantId: "",
      blockId: "",
      areaId: "",
      roomId: "",
      isActive: true,
    };
  }

  if (section === "critical-parameters") {
    return {
      equipmentId: "",
      parameterCode: "",
      parameterName: "",
      unitOfMeasure: "",
      parameterType: "",
      tenantId: "",
      plantId: "",
      isActive: true,
    };
  }

  if (section === "critical-parameter-limits") {
    return {
      parameterLimitCode: "",
      parameterId: "",
      parameterType: "",
      equipmentId: "",
      tenantId: "",
      plantId: "",
      lowCriticalValue: "",
      highCriticalValue: "",
      alarmEnabled: false,
      booleanValue: "",
      enumValue: "",
      stringValue: "",
      isActive: true,
    };
  }

  return {
    productCode: "",
    productName: "",
    tenantId: "",
    plantId: "",
    isActive: true,
  };
};

const option = (value: string, label = value) => ({ value, label });
const emptyOption = (label: string) => ({ value: "", label });

const toScopedId = (equipmentId: string, value: string, suffix = "") => {
  const equipment = equipmentId.trim();
  const raw = value.trim();
  if (!equipment || !raw) return raw;
  return `${equipment}::${raw}${suffix}`;
};

export default function IiotMasterCreateForm({
  section,
}: {
  section: IiotMasterSection;
}) {
  const router = useRouter();
  const { tenants } = useTenants();
  const { data: topology } = usePlantTopology();
  const { records } = useIiotMasterData();
  const [values, setValues] = useState<Values>(() => emptyValues(section));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const tenantId = String(values.tenantId ?? "");
  const plantId = String(values.plantId ?? "");
  const blockId = String(values.blockId ?? "");
  const equipmentId = String(values.equipmentId ?? "");

  const fields = useMemo<Field[]>(() => {
    const tenantOptions = [
      emptyOption("Select Tenant"),
      ...tenants
        .filter((tenant) => tenant.isActive)
        .map((tenant) =>
          option(tenant.tenantId, `${tenant.companyName} (${tenant.tenantId})`),
        ),
    ];
    const plantOptions = [
      emptyOption("Select Plant"),
      ...topology.plants
        .filter(
          (plant) => plant.isActive && (!tenantId || plant.tenantId === tenantId),
        )
        .map((plant) => option(plant.plantId, `${plant.plantName} (${plant.plantId})`)),
    ];
    const blockOptions = [
      emptyOption("Select Block"),
      ...topology.blocks
        .filter(
          (block) =>
            block.isActive &&
            (!tenantId || block.tenantId === tenantId) &&
            (!plantId || block.plantId === plantId),
        )
        .map((block) => option(block.blockId, `${block.blockName} (${block.blockId})`)),
    ];
    const areaOptions = [
      emptyOption("Select Area"),
      ...topology.areas
        .filter(
          (area) =>
            area.isActive &&
            (!tenantId || area.tenantId === tenantId) &&
            (!plantId || area.plantId === plantId) &&
            (!blockId || area.blockId === blockId),
        )
        .map((area) => option(area.areaId, `${area.areaName} (${area.areaId})`)),
    ];
    const roomOptions = [
      emptyOption("Select Room"),
      ...topology.rooms
        .filter(
          (room) =>
            room.isActive &&
            (!tenantId || room.tenantId === tenantId) &&
            (!plantId || room.plantId === plantId) &&
            (!values.areaId || room.areaId === values.areaId),
        )
        .map((room) => option(room.roomId, `${room.roomName} (${room.roomId})`)),
    ];
    const equipmentOptions = [
      emptyOption("Select Equipment"),
      ...records.equipments
        .filter((equipment) => equipment.isActive && (!tenantId || equipment.tenantId === tenantId))
        .map((equipment) =>
          option(
            equipment.equipmentId,
            `${equipment.equipmentName} (${equipment.equipmentCode})`,
          ),
        ),
    ];
    const parameterOptions = [
      emptyOption("Select Critical Parameter"),
      ...records["critical-parameters"]
        .filter((parameter) => parameter.isActive)
        .map((parameter) =>
          option(
            parameter.parameterId,
            `${parameter.parameterName} (${parameter.parameterCode})`,
          ),
        ),
    ];
    const limitType = String(values.parameterType ?? "").toUpperCase();

    if (section === "equipments") {
      return [
        { id: "tenantId", label: "Tenant", kind: "select", options: tenantOptions },
        { id: "plantId", label: "Plant", kind: "select", options: plantOptions },
        { id: "blockId", label: "Block", kind: "select", options: blockOptions },
        { id: "areaId", label: "Area", kind: "select", options: areaOptions },
        { id: "roomId", label: "Room", kind: "select", options: roomOptions },
        { id: "equipmentCode", label: "Equipment Code", placeholder: "Enter equipment code" },
        { id: "equipmentName", label: "Equipment Name", placeholder: "Enter equipment name" },
      ];
    }

    if (section === "critical-parameters") {
      return [
        { id: "equipmentId", label: "Equipment ID", kind: "select", options: equipmentOptions },
        { id: "parameterCode", label: "Parameter Code", placeholder: "Enter parameter code" },
        { id: "parameterName", label: "Parameter Name", placeholder: "Enter parameter name" },
        { id: "unitOfMeasure", label: "Unit Of Measure", placeholder: "Enter unit of measure" },
        { id: "parameterType", label: "Parameter Type", kind: "select", options: parameterTypeOptions },
      ];
    }

    if (section === "critical-parameter-limits") {
      const baseFields: Field[] = [
        { id: "parameterLimitCode", label: "Parameter Limit Code", placeholder: "Enter parameter limit code" },
        { id: "parameterId", label: "Critical Parameter", kind: "select", options: parameterOptions },
        { id: "parameterType", label: "Parameter Type", placeholder: "Auto populated from parameter", disabled: true },
        { id: "alarmEnabled", label: "Alarm Enabled", kind: "checkbox" },
      ];
      if (limitType === "INT" || limitType === "FLOAT" || limitType === "RANGE") {
        return [
          ...baseFields,
          { id: "lowCriticalValue", label: "Low Critical Value", kind: "number", placeholder: "5.5" },
          { id: "highCriticalValue", label: "High Critical Value", kind: "number", placeholder: "7.5" },
        ];
      }
      if (limitType === "BOOLEAN") {
        return [
          ...baseFields,
          { id: "booleanValue", label: "Boolean Value", kind: "select", options: booleanOptions },
        ];
      }
      if (limitType === "ENUM") {
        return [
          ...baseFields,
          { id: "enumValue", label: "Enum Value", placeholder: "Enter enum value(s)" },
        ];
      }
      if (limitType === "STRING") {
        return [
          ...baseFields,
          { id: "stringValue", label: "String Value", placeholder: "Enter text value" },
        ];
      }
      return baseFields;
    }

    return [
      { id: "tenantId", label: "Tenant", kind: "select", options: tenantOptions },
      { id: "plantId", label: "Plant", kind: "select", options: plantOptions },
      { id: "productCode", label: "Product Code", placeholder: "Enter product code" },
      { id: "productName", label: "Product Name", placeholder: "Enter product name" },
    ];
  }, [
    blockId,
    equipmentId,
    plantId,
    records,
    section,
    tenantId,
    tenants,
    topology,
    values.areaId,
  ]);

  const change = (field: string, value: string | boolean) => {
    setValues((current) => {
      const next = { ...current, [field]: value };
      if (field === "tenantId") {
        Object.assign(next, {
          plantId: "",
          blockId: "",
          areaId: "",
          roomId: "",
          equipmentId: "",
          parameterId: "",
        });
      }
      if (field === "plantId") {
        Object.assign(next, {
          blockId: "",
          areaId: "",
          roomId: "",
          equipmentId: "",
          parameterId: "",
        });
      }
      if (field === "blockId") Object.assign(next, { areaId: "", roomId: "" });
      if (field === "areaId") Object.assign(next, { roomId: "" });
      if (field === "equipmentId") {
        const equipment = records.equipments.find(
          (item) => item.equipmentId === value,
        );
        Object.assign(next, {
          tenantId: equipment?.tenantId ?? next.tenantId,
          plantId: equipment?.plantId ?? next.plantId,
          parameterId: "",
        });
      }
      if (field === "parameterId") {
        const parameter = records["critical-parameters"].find(
          (item) => item.parameterId === value,
        );
        const equipment = records.equipments.find(
          (item) => item.equipmentId === parameter?.equipmentId,
        );
        Object.assign(next, {
          equipmentId: parameter?.equipmentId ?? equipment?.equipmentId ?? next.equipmentId,
          tenantId: parameter?.tenantId ?? equipment?.tenantId ?? next.tenantId,
          plantId: parameter?.plantId ?? equipment?.plantId ?? next.plantId,
          parameterType: parameter?.parameterType ?? "",
          lowCriticalValue: "",
          highCriticalValue: "",
          booleanValue: "",
          enumValue: "",
          stringValue: "",
        });
      }
      return next;
    });
    setErrors((current) => ({ ...current, [field]: "" }));
  };

  const buildPayload = () =>
    Object.fromEntries(
      Object.entries(values).map(([key, value]) => [
        key,
        typeof value === "string" ? value.trim() : value,
      ]),
    );

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload = buildPayload();

    setIsSubmitting(true);
    try {
      if (section === "equipments") {
        const equipmentPayload = {
          ...payload,
          equipmentId:
            typeof payload.equipmentCode === "string"
              ? payload.equipmentCode
              : "",
        };
        const parsed = createIiotAssetSchema.safeParse(equipmentPayload);
        if (!parsed.success) {
          setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
          setIsSubmitting(false);
          return;
        }
        await createIiotAsset(parsed.data);
      } else if (section === "critical-parameters") {
        const selectedEquipment = records.equipments.find(
          (item) => item.equipmentId === String(payload.equipmentId ?? ""),
        );
        const scopedParameterId = toScopedId(
          String(payload.equipmentId ?? selectedEquipment?.equipmentId ?? ""),
          String(payload.parameterCode ?? ""),
        );
        const parameterPayload = {
          ...payload,
          parameterId: scopedParameterId,
          tenantId: String(payload.tenantId ?? selectedEquipment?.tenantId ?? ""),
          plantId: String(payload.plantId ?? selectedEquipment?.plantId ?? ""),
        };
        const parsed = createCriticalParameterSchema.safeParse(parameterPayload);
        if (!parsed.success) {
          setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
          setIsSubmitting(false);
          return;
        }
        await createCriticalParameter(parsed.data);
      } else if (section === "critical-parameter-limits") {
        const selectedParameter = records["critical-parameters"].find(
          (item) => item.parameterId === String(payload.parameterId ?? ""),
        );
        const selectedEquipment = records.equipments.find(
          (item) => item.equipmentId === String(selectedParameter?.equipmentId ?? payload.equipmentId ?? ""),
        );
        const limitType = String(payload.parameterType ?? selectedParameter?.parameterType ?? "").toUpperCase();
        const isBooleanTrue = String(payload.booleanValue ?? "").toLowerCase() === "true";
        const scopedLimitId = toScopedId(
          String(payload.equipmentId ?? selectedParameter?.equipmentId ?? selectedEquipment?.equipmentId ?? ""),
          String(payload.parameterLimitCode ?? payload.parameterId ?? ""),
          "::LIM",
        );
        const limitPayload = {
          ...payload,
          parameterLimitId: scopedLimitId,
          parameterType: limitType,
          equipmentId: String(payload.equipmentId ?? selectedParameter?.equipmentId ?? selectedEquipment?.equipmentId ?? ""),
          tenantId: String(payload.tenantId ?? selectedParameter?.tenantId ?? selectedEquipment?.tenantId ?? ""),
          plantId: String(payload.plantId ?? selectedParameter?.plantId ?? selectedEquipment?.plantId ?? ""),
          lowCriticalValue:
            limitType === "INT" || limitType === "FLOAT" || limitType === "RANGE"
              ? payload.lowCriticalValue
              : limitType === "BOOLEAN"
                ? (isBooleanTrue ? 1 : 0)
                : undefined,
          highCriticalValue:
            limitType === "INT" || limitType === "FLOAT" || limitType === "RANGE"
              ? payload.highCriticalValue
              : limitType === "BOOLEAN"
                ? (isBooleanTrue ? 1 : 0)
                : undefined,
          booleanValue: limitType === "BOOLEAN" ? isBooleanTrue : undefined,
          enumValue: limitType === "ENUM" ? String(payload.enumValue ?? "") : undefined,
          stringValue: limitType === "STRING" ? String(payload.stringValue ?? "") : undefined,
        };
        const parsed = createCriticalParameterLimitSchema.safeParse(limitPayload);
        if (!parsed.success) {
          setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
          setIsSubmitting(false);
          return;
        }
        await createCriticalParameterLimit(parsed.data);
      } else {
        const productPayload = {
          ...payload,
          productId:
            typeof payload.productCode === "string"
              ? payload.productCode
              : "",
        };
        const parsed = createProductMasterSchema.safeParse(productPayload);
        if (!parsed.success) {
          setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
          setIsSubmitting(false);
          return;
        }
        await createProductMaster(parsed.data);
      }

      setNotification({
        message: `${sectionLabels[section]} created successfully.`,
        variant: "success",
      });
      window.setTimeout(() => {
        router.push(sectionReturnRoutes[section]);
        router.refresh();
      }, 700);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : `Unable to create ${sectionLabels[section].toLowerCase()}.`,
        variant: "error",
      });
      setIsSubmitting(false);
    }
  };

  const clear = () => {
    setValues(emptyValues(section));
    setErrors({});
  };

  const renderField = (field: Field) => {
    if (field.kind === "checkbox") {
      return (
        <label key={field.id} className="flex h-9 items-center gap-2 self-end">
          <input
            type="checkbox"
            checked={Boolean(values[field.id])}
            onChange={(event) => change(field.id, event.target.checked)}
            className="h-4 w-4 rounded border-[#BFD0E4] text-primary"
          />
          <span className="type-filter-label text-text-heading">{field.label}</span>
        </label>
      );
    }

    return (
      <label key={field.id} htmlFor={field.id} className="grid gap-2">
        <span className="type-filter-label text-text-heading">{field.label}</span>
        {field.kind === "select" ? (
          <>
            <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
              <select
                id={field.id}
                value={String(values[field.id] ?? "")}
                onChange={(event) => change(field.id, event.target.value)}
                disabled={field.disabled}
                className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed"
              >
                {field.options?.map((item) => (
                  <option key={`${field.id}-${item.value}-${item.label}`} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
              <CaretDown size={11} weight="bold" className="pointer-events-none absolute right-3 text-text-secondary" />
            </span>
            {errors[field.id] ? <span className="text-[9px] text-danger">{errors[field.id]}</span> : null}
          </>
        ) : (
          <TextField
            id={field.id}
            type={field.kind === "number" ? "number" : "text"}
            placeholder={field.placeholder}
            value={String(values[field.id] ?? "")}
            readOnly={field.disabled}
            onChange={(event) => change(field.id, event.target.value)}
            error={errors[field.id]}
            containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
            inputClassName="type-filter-value placeholder:text-text-secondary read-only:cursor-not-allowed"
            hintClassName="text-[9px]"
          />
        )}
      </label>
    );
  };

  return (
    <>
      <form
        onSubmit={submit}
        className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]"
      >
        <div className="border-b border-[#E3E9F0] px-6 py-5">
          <h2 className="text-[14px] font-semibold text-text-heading">
            Enter {sectionLabels[section]} Details
          </h2>
          <p className="mt-2 text-[9px] text-text-secondary">
            Fill out the required details to create a {sectionLabels[section].toLowerCase()}.
          </p>
        </div>
        <div className="grid gap-x-10 gap-y-6 px-6 py-5 md:grid-cols-2 xl:grid-cols-3">
          {fields.map(renderField)}
          <div className="flex justify-end gap-3 md:col-span-2 xl:col-span-3">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-4"
              paddingY="py-0"
              className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
              prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />}
              onClick={clear}
            >
              Clear All
            </Button>
            <Button
              type="submit"
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-5"
              paddingY="py-0"
              className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
              isLoading={isSubmitting}
            >
              Create {sectionLabels[section]}
            </Button>
          </div>
        </div>
      </form>
      <Snackbar
        open={Boolean(notification.message)}
        title={
          notification.variant === "success"
            ? `${sectionLabels[section]} created`
            : `Unable to create ${sectionLabels[section].toLowerCase()}`
        }
        message={notification.message}
        variant={notification.variant}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </>
  );
}
