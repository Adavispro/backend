import equipmentIcon from "@/assets/icons/equipment-icon.svg";
import idIcon from "@/assets/icons/id-icon.svg";
import lotIcon from "@/assets/icons/lot-icon.svg";
import productNameIcon from "@/assets/icons/product-name-icon.svg";
import roomIcon from "@/assets/icons/room-icon.svg";
import type { CompactEventItem } from "@/components/ui/CompactEventsCard";
import type { MonitoringField } from "./types";

const option = (value: string, label: string) => ({ value, label });

export const monitoringFields: MonitoringField[] = [
  {
    id: "plantId",
    label: "Plant Name",
    placeholder: "Select Plant Name",
    icon: equipmentIcon,
    options: [option("", "Select Plant Name")],
  },
  {
    id: "areaId",
    label: "Area Name",
    icon: idIcon,
    placeholder: "Select Area Name",
    options: [option("", "Select Area Name")],
  },
  {
    id: "equipmentId",
    label: "Equipment ID",
    placeholder: "Select Equipment ID",
    icon: productNameIcon,
    options: [option("", "Select Equipment ID")],
  },
  {
    id: "batchNo",
    label: "Batch No",
    placeholder: "Select Batch No",
    icon: roomIcon,
    options: [option("", "Select Batch No")],
    required: true,
  },
  {
    id: "lotNo",
    label: "Lot Number",
    placeholder: "Select Lot Number",
    icon: lotIcon,
    options: [option("", "Select Lot Number")],
    required: false,
  },
];

export const recentEvents: CompactEventItem[] = [
  { label: "High Discharge Temperature", value: "8:00 AM", markerColor: "#EF4444" },
  { label: "Compressor Started", value: "9:00 AM", markerColor: "#38C172" },
  { label: "Vibration level normal", value: "8:00 AM", markerColor: "#38C172" },
  { label: "Audit Drain Activated", value: "9:00 AM", markerColor: "#38C172" },
  { label: "Maintenance completed", value: "9:00 AM", markerColor: "#0B63C7" },
];

export const activeAlarms: CompactEventItem[] = [
  { label: "High Discharge Temperature", status: "Critical", value: "8:25 AM" },
  { label: "Vibration Level High", status: "Warning", value: "8:25 AM" },
  { label: "High Pressure", status: "Warning", value: "8:25 AM" },
];

export function getInitialMonitoringValues(fields = monitoringFields) {
  return Object.fromEntries(fields.map((field) => [field.id, ""]));
}

export function isCompleteMonitoringSelection(
  values: Record<string, string>,
  fields = monitoringFields,
) {
  return fields.every((field) => {
    if (field.required === false) return true;
    return Boolean(values[field.id]);
  });
}
