import type { StaticImageData } from "next/image";

export type MonitoringValues = Record<string, string>;

export interface MonitoringOption {
  value: string;
  label: string;
}

export interface MonitoringField {
  id: string;
  label: string;
  placeholder: string;
  icon: StaticImageData;
  options: MonitoringOption[];
  kind?: "select" | "date";
  required?: boolean;
  disabled?: boolean;
}
