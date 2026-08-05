"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Bell,
  CaretDown,
  CaretRight,
  Gear,
  LockKey,
  NotePencil,
  UserCircle,
} from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import {
  readTopBarSelection,
  TOP_BAR_SELECTION_EVENT,
} from "@/utils/topBarSelection";
import {
  readSelectedPlantId,
  writeSelectedPlantId,
} from "@/utils/plantSelection";
import LogoutButton from "./LogoutButton";

export interface ModuleTopBarProps {
  title?: string;
  breadcrumbs?: Array<{
    label: string;
    href?: string;
    active?: boolean;
  }>;
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

  return {
    id,
    label: name ? `${name} (${id})` : id,
  };
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

export default function ModuleTopBar({ title, breadcrumbs }: ModuleTopBarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const loginContext = useLoginContext();
  const currentUser = useCurrentUser();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [selectedPlantId, setSelectedPlantId] = useState("");
  const settingsRef = useRef<HTMLDivElement>(null);
  const userInitial =
    currentUser?.firstName?.trim().charAt(0).toUpperCase() ||
    currentUser?.username?.trim().charAt(0).toUpperCase() ||
    "V";
  const userName =
    [currentUser?.firstName, currentUser?.lastName]
      .map((value) => value?.trim())
      .filter(Boolean)
      .join(" ") ||
    currentUser?.username ||
    currentUser?.userId ||
    "User";
  const userStatus = currentUser?.status ?? currentUser?.lifecycleStatus ?? "Active";
  const isSelectedMachineRoute =
    pathname === ROUTES.iiotMonitoring || pathname === ROUTES.iiotAnalytics;
  const selectedEquipment = useSyncExternalStore(
    (onStoreChange) => {
      window.addEventListener(TOP_BAR_SELECTION_EVENT, onStoreChange);

      return () => {
        window.removeEventListener(TOP_BAR_SELECTION_EVENT, onStoreChange);
      };
    },
    () => (isSelectedMachineRoute ? readTopBarSelection(pathname) : null),
    () => null,
  );
  const selectedMachineBreadcrumbs = useMemo(() => {
    if (selectedEquipment === null || !isSelectedMachineRoute) {
      return undefined;
    }

    const rootBreadcrumb =
      pathname === ROUTES.iiotMonitoring
        ? { label: "Analytics", href: ROUTES.iiotMonitoring }
        : { label: "OEE", href: ROUTES.iiotAnalytics };

    return [
      rootBreadcrumb,
      { label: selectedEquipment, active: true },
    ];
  }, [isSelectedMachineRoute, selectedEquipment]);
  const visibleBreadcrumbs = selectedMachineBreadcrumbs ?? breadcrumbs;

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
  const activePlantLabel =
    plantOptions.find((option) => option.id === activePlantId)?.label ||
    (activePlantId ? `Plant (${activePlantId})` : "Plant");

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

  const handleBack = () => {
    if (window.history.length > 1) {
      router.back();
      return;
    }

    router.push(ROUTES.modules);
  };

  useEffect(() => {
    if (!isSettingsOpen) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (settingsRef.current && !settingsRef.current.contains(target)) {
        setIsSettingsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsSettingsOpen(false);
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isSettingsOpen]);

  return (
    <header className="flex h-20 shrink-0 items-center gap-6 px-5 sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          aria-label="Go back"
          onClick={handleBack}
          className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-white/70 bg-white/45 text-primary shadow-[0_8px_18px_rgba(35,50,70,0.08)] transition-colors hover:bg-white/75 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/35"
        >
          <ArrowLeft size={16} weight="bold" />
        </button>

        {visibleBreadcrumbs !== undefined && visibleBreadcrumbs.length > 0 ? (
          <nav aria-label="Breadcrumb" className="min-w-0">
            <ol className="flex min-w-0 items-center gap-2">
              {visibleBreadcrumbs.map((breadcrumb, index) => {
                const isLast = index === visibleBreadcrumbs.length - 1;

                return (
                  <li
                    key={`${breadcrumb.label}-${index}`}
                    className="flex min-w-0 items-center gap-2"
                  >
                    {"href" in breadcrumb && breadcrumb.href !== undefined && !isLast && !("active" in breadcrumb && breadcrumb.active) ? (
                      <Link
                        href={breadcrumb.href}
                        className="truncate text-sm font-semibold text-text-secondary transition-colors hover:text-primary"
                      >
                        {breadcrumb.label}
                      </Link>
                    ) : (
                      <span
                        aria-current={isLast || ("active" in breadcrumb && breadcrumb.active) ? "page" : undefined}
                        className={`truncate text-sm font-semibold ${
                          isLast || ("active" in breadcrumb && breadcrumb.active)
                            ? "text-primary"
                            : "text-text-secondary"
                        }`}
                      >
                        {breadcrumb.label}
                      </span>
                    )}
                    {!isLast ? (
                      <span
                        aria-hidden="true"
                        className="shrink-0 text-sm font-semibold text-text-tertiary"
                      >
                        ›
                      </span>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          </nav>
        ) : title ? (
          <h1 className="min-w-0 truncate text-lg font-semibold text-[#111827]">
            {title}
          </h1>
        ) : null}
      </div>

      <div className="ml-auto flex items-center gap-2 text-[#3f464f]">
        {null}

        <button
          type="button"
          aria-label="Notifications"
          className="relative flex h-8 w-8 items-center justify-center rounded-lg transition-colors hover:bg-white/50"
        >
          <Bell size={17} weight="regular" />
          <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full border border-[#eef5fd] bg-[#d82c2c]" />
        </button>

        <div
          ref={settingsRef}
          className="relative"
          onMouseEnter={() => setIsSettingsOpen(true)}
          onMouseLeave={() => setIsSettingsOpen(false)}
        >
          <button
            type="button"
            aria-label="Settings"
            aria-haspopup="menu"
            aria-expanded={isSettingsOpen}
            onClick={() => setIsSettingsOpen((isOpen) => !isOpen)}
            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors hover:bg-white/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
          >
            <Gear size={17} weight="regular" />
          </button>

          {isSettingsOpen ? (
            <div
              className="absolute right-0 top-full z-[130] w-56 pt-2"
            >
              <div
                role="menu"
                className="relative rounded-xl border border-white/80 bg-white/95 p-2 shadow-[0_16px_42px_rgba(48,69,94,0.18)] backdrop-blur-xl"
              >
                <span
                  aria-hidden="true"
                  className="absolute -top-1.5 right-3 h-3 w-3 rotate-45 border-l border-t border-white/80 bg-white"
                />
                {[
                  {
                    label: "Edit Profile",
                    description: "Update personal details",
                    icon: NotePencil,
                    onSelect: () => {
                      setIsSettingsOpen(false);
                      router.push(ROUTES.editProfile);
                    },
                  },
                  {
                    label: "Update Password",
                    description: "Change account password",
                    icon: LockKey,
                    onSelect: () => {
                      setIsSettingsOpen(false);
                      router.push(ROUTES.updatePassword);
                    },
                  },
                ].map((item) => {
                  const Icon = item.icon;

                  return (
                    <button
                      key={item.label}
                      type="button"
                      role="menuitem"
                      onClick={item.onSelect}
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-[#F4F8FE]"
                    >
                      <span className="grid h-8 w-8 place-items-center rounded-lg bg-[#E6F1FF] text-primary">
                        <Icon size={16} weight="regular" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-xs font-semibold text-text-heading">
                          {item.label}
                        </span>
                        <span className="block truncate text-[10px] font-medium text-text-secondary">
                          {item.description}
                        </span>
                      </span>
                      <CaretRight size={12} weight="bold" className="text-primary" />
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}
        </div>

        <LogoutButton />

        <div className="mx-1 h-7 w-px bg-[#cfd8e4]" />

        <div className="group relative">
          <button
            type="button"
            aria-label="User profile"
            className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-[#d3ad91] text-xs font-semibold text-white shadow-[0_0_0_1px_#cbd4df] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
          >
            {userInitial}
          </button>

          <div className="pointer-events-none absolute right-0 top-full z-[130] w-72 pt-2 opacity-0 transition-opacity duration-150 group-hover:pointer-events-auto group-hover:opacity-100">
            <div className="rounded-2xl border border-white/80 bg-white/95 p-4 shadow-[0_18px_44px_rgba(48,69,94,0.18)] backdrop-blur-xl">
              <span
                aria-hidden="true"
                className="absolute right-3 top-[-5px] h-3 w-3 rotate-45 border-l border-t border-white/80 bg-white"
              />
              <div className="flex items-center gap-3">
                <div className="grid h-12 w-12 place-items-center rounded-full border-2 border-white bg-[#d3ad91] text-base font-semibold text-white shadow-[0_0_0_1px_#cbd4df]">
                  {userInitial}
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-text-heading">
                    {userName}
                  </p>
                  <p className="truncate text-[11px] font-medium text-text-secondary">
                    {currentUser?.email ?? "No email available"}
                  </p>
                </div>
              </div>

              <div className="mt-4 grid gap-2 rounded-xl bg-[#F4F8FE] p-3">
                <ProfileDetail label="User ID" value={currentUser?.userId ?? "-"} />
                <ProfileDetail label="Tenant" value={currentUser?.tenantId ?? "-"} />
                <ProfileDetail label="Status" value={userStatus} />
              </div>

              <div className="mt-3 flex items-center gap-2 text-[10px] font-semibold text-primary">
                <UserCircle size={14} weight="regular" />
                Signed in profile
              </div>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}

function ProfileDetail({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-[10px] font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {label}
      </span>
      <span className="truncate text-right text-[11px] font-semibold text-text-heading">
        {value}
      </span>
    </div>
  );
}
