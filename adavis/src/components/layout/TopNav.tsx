"use client";

import Image from "next/image";
import { useEffect, useMemo, useState } from "react";
import { Bell, CaretDown, Gear } from "@phosphor-icons/react";
import logoExtended from "@/assets/logo/logo-extended.svg";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { readSelectedPlantId, writeSelectedPlantId } from "@/utils/plantSelection";
import LogoutButton from "./LogoutButton";

export interface TopNavProps {
  /** Single uppercase letter shown in the avatar circle */
  userInitial?: string;
}

interface PlantOption {
  id: string;
  label: string;
}

const asRecord = (value: unknown): Record<string, unknown> | null =>
  value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;

const toPlantOption = (value: unknown): PlantOption | null => {
  const record = asRecord(value);
  if (!record) return null;

  const id =
    typeof record.plantId === "string" && record.plantId.trim()
      ? record.plantId.trim()
      : typeof record.id === "string" && record.id.trim()
        ? record.id.trim()
        : "";
  if (!id) return null;

  const name =
    typeof record.plantName === "string" && record.plantName.trim()
      ? record.plantName.trim()
      : typeof record.name === "string" && record.name.trim()
        ? record.name.trim()
        : "";

  return { id, label: name ? `${name} (${id})` : id };
};

const uniquePlants = (values: unknown[]): PlantOption[] => {
  const seen = new Set<string>();
  const options: PlantOption[] = [];

  values.forEach((value) => {
    const option = toPlantOption(value);
    if (!option || seen.has(option.id)) return;
    seen.add(option.id);
    options.push(option);
  });

  return options;
};

const plantIdsToOptions = (plantIds: string[]) =>
  Array.from(new Set(plantIds.filter(Boolean).map((value) => value.trim())))
    .filter(Boolean)
    .map((id) => ({ id, label: id }));

export default function TopNav({ userInitial = "N" }: TopNavProps) {
  const loginContext = useLoginContext();
  const [selectedPlantId, setSelectedPlantId] = useState("");

  const plantOptions = useMemo(() => {
    const fromAssignedPlants = uniquePlants(loginContext?.assignedPlants ?? []);
    if (fromAssignedPlants.length > 0) return fromAssignedPlants;
    return plantIdsToOptions(loginContext?.assignedPlantIds ?? []);
  }, [loginContext?.assignedPlantIds, loginContext?.assignedPlants]);

  const defaultPlantId = useMemo(() => {
    const contextSelectedPlant =
      typeof loginContext?.selectedPlantId === "string"
        ? loginContext.selectedPlantId.trim()
        : "";
    return contextSelectedPlant || plantOptions[0]?.id || "";
  }, [loginContext?.selectedPlantId, plantOptions]);

  const activePlantId = selectedPlantId || defaultPlantId;
  const showPlantSwitcher = plantOptions.length > 0;

  useEffect(() => {
    if (!showPlantSwitcher) {
      setSelectedPlantId("");
      return;
    }

    const storedPlantId = readSelectedPlantId();
    const nextPlantId =
      storedPlantId && plantOptions.some((option) => option.id === storedPlantId)
        ? storedPlantId
        : defaultPlantId;

    setSelectedPlantId(nextPlantId);
  }, [defaultPlantId, plantOptions, showPlantSwitcher]);

  const handlePlantChange = (value: string) => {
    setSelectedPlantId(value);
    writeSelectedPlantId(value);
  };

  return (
    <header className="sticky top-0 z-50 h-[72px] border-b border-[#edf0f4] bg-white">
      <div className="mx-auto flex h-full w-full max-w-[1380px] items-center justify-between px-5 sm:px-6">
        <Image
          src={logoExtended}
          alt="ADAVIS"
          height={44}
          width={124}
          className="h-11 w-auto"
          priority
        />

        <div className="flex items-center gap-1.5">
          {null}

          <button
            aria-label="Notifications"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-[#4f545b] transition-colors hover:bg-surface-muted"
          >
            <Bell size={16} weight="regular" />
          </button>

          <button
            aria-label="Settings"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-[#4f545b] transition-colors hover:bg-surface-muted"
          >
            <Gear size={16} weight="regular" />
          </button>

          <LogoutButton />

          <div className="mx-1 h-7 w-px bg-[#cfd8e4]" />

          <button
            aria-label="User menu"
            className="ml-1 flex h-8 w-8 select-none items-center justify-center rounded-full border-2 border-white bg-[#d7b49b] text-xs font-semibold text-white shadow-[0_0_0_1px_#d8dde5]"
          >
            {userInitial}
          </button>
        </div>
      </div>
    </header>
  );
}
