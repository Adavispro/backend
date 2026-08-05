"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { CaretDown, CaretUp, Check } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { getPermissionMatrix, getRolePermissions, saveRolePermission } from "../api";
import type { ModuleCatalog, RolePermission, RolePermissionRequest } from "../api/types";

const actions = ["READ", "WRITE", "MODIFY", "APPROVE", "DEACTIVATE"] as const;
type Selection = Record<string, Record<string, { actions: string[]; features: Record<string, string[]> }>>;

const fromPermissions = (permissions: RolePermission[]): Selection =>
  Object.fromEntries(permissions.map((permission) => [
    permission.moduleId,
    Object.fromEntries(permission.screenPermissions.map((screen) => [screen.screenId, {
      actions: screen.actions,
      features: Object.fromEntries(screen.featurePermissions.map((feature) => [feature.featureId, feature.actions])),
    }])),
  ]));

function Toggle({ checked, label, onClick }: { checked: boolean; label: string; onClick: () => void }) {
  return <button type="button" onClick={onClick} className="inline-flex h-8 min-w-[76px] items-center justify-center gap-2 rounded-[4px] border border-[#D6E1ED] bg-white/45 px-3 text-[9px] font-medium text-text-secondary"><span className={`grid h-3.5 w-3.5 place-items-center rounded-[2px] border ${checked ? "border-primary bg-primary text-white" : "border-[#C8D4E1] bg-white"}`}>{checked ? <Check size={9} weight="bold" /> : null}</span><span className="leading-none">{label}</span></button>;
}

export default function RolePermissionsEditor({ roleId, onSaved }: { roleId: string; onSaved?: () => void }) {
  const [modules, setModules] = useState<ModuleCatalog[]>([]);
  const [selection, setSelection] = useState<Selection>({});
  const [existingModules, setExistingModules] = useState<string[]>([]);
  const [activeModuleId, setActiveModuleId] = useState("");
  const [collapsedScreens, setCollapsedScreens] = useState<Set<string>>(() => new Set());
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState({ text: "", variant: "success" as "success" | "error" });

  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([getPermissionMatrix(controller.signal), getRolePermissions(roleId, controller.signal)])
      .then(([matrix, permissions]) => {
        setModules(matrix.modules);
        setActiveModuleId(matrix.modules[0]?.moduleId ?? "");
        setSelection(fromPermissions(permissions));
        setExistingModules(permissions.map((permission) => permission.moduleId));
      })
      .catch((error) => setMessage({ text: error instanceof Error ? error.message : "Unable to load role permissions.", variant: "error" }))
      .finally(() => { if (!controller.signal.aborted) setIsLoading(false); });
    return () => controller.abort();
  }, [roleId]);

  const activeModule = useMemo(() => modules.find((module) => module.moduleId === activeModuleId) ?? modules[0], [activeModuleId, modules]);
  const toggle = (moduleId: string, screenId: string, action: string, featureId?: string) => setSelection((current) => {
    const moduleSelection = current[moduleId] ?? {};
    const screenSelection = moduleSelection[screenId] ?? { actions: [], features: {} };
    const currentActions = featureId ? screenSelection.features[featureId] ?? [] : screenSelection.actions;
    const nextActions = currentActions.includes(action) ? currentActions.filter((item) => item !== action) : [...currentActions, action];
    return { ...current, [moduleId]: { ...moduleSelection, [screenId]: featureId ? { ...screenSelection, features: { ...screenSelection.features, [featureId]: nextActions } } : { ...screenSelection, actions: nextActions } } };
  });

  const grantScreen = (module: ModuleCatalog, screenId: string) => setSelection((current) => {
    const screen = module.screens.find((item) => item.screenId === screenId);
    if (!screen) return current;
    const moduleSelection = current[module.moduleId] ?? {};
    return { ...current, [module.moduleId]: { ...moduleSelection, [screenId]: { actions: [...actions], features: Object.fromEntries(screen.features.map((feature) => [feature.featureId, [...actions]])) } } };
  });

  const clearScreen = (moduleId: string, screenId: string) => setSelection((current) => ({
    ...current,
    [moduleId]: {
      ...(current[moduleId] ?? {}),
      [screenId]: { actions: [], features: {} },
    },
  }));

  const isScreenEnabled = (moduleId: string, screenId: string) => {
    const screen = selection[moduleId]?.[screenId];
    return Boolean(screen && (screen.actions.length > 0 || Object.values(screen.features).some((featureActions) => featureActions.length > 0)));
  };

  const toggleScreenCollapsed = (screenId: string) => {
    setCollapsedScreens((current) => {
      const next = new Set(current);
      if (next.has(screenId)) next.delete(screenId);
      else next.add(screenId);
      return next;
    });
  };

  const clearAll = () => setSelection({});
  const save = async () => {
    setIsSaving(true);
    try {
      const moduleIds = Array.from(new Set([...existingModules, ...Object.keys(selection)]));
      await Promise.all(moduleIds.map((moduleId) => {
        const screens = Object.entries(selection[moduleId] ?? {}).map(([screenId, value]) => ({
          screenId,
          actions: value.actions,
          featurePermissions: Object.entries(value.features).filter(([, selected]) => selected.length > 0).map(([featureId, selected]) => ({ featureId, actions: selected })),
        })).filter((screen) => screen.actions.length > 0 || screen.featurePermissions.length > 0);
        const request: RolePermissionRequest = { moduleId, version: 1, isActive: screens.length > 0, screenPermissions: screens };
        return saveRolePermission(roleId, request);
      }));
      setMessage({ text: "Role permissions saved successfully.", variant: "success" });
      onSaved?.();
    } catch (error) {
      setMessage({ text: error instanceof ApiError ? error.message : "Unable to save role permissions.", variant: "error" });
    } finally { setIsSaving(false); }
  };

  if (isLoading) return <section className="module-glass-panel rounded-lg p-8 text-center text-[11px] text-text-secondary">Loading permission catalog...</section>;

  return <section className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
    <div className="flex items-center justify-between px-6 py-5"><h2 className="text-[14px] font-semibold text-text-heading">Set Module Permissions</h2><Button size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9" prefixIcon={<Check size={12} weight="bold" />} onClick={() => modules.forEach((module) => module.screens.forEach((screen) => grantScreen(module, screen.screenId)))}>Grant All Permissions</Button></div>
    <div className="flex gap-7 border-b border-[#DCE4ED] px-6">{modules.map((module) => <button key={module.moduleId} type="button" onClick={() => setActiveModuleId(module.moduleId)} className={`border-b-2 px-1 pb-3 text-[10px] font-semibold ${activeModule?.moduleId === module.moduleId ? "border-primary text-primary" : "border-transparent text-text-secondary"}`}>{module.moduleName}</button>)}</div>
    <div>{activeModule?.screens.map((screen) => {
      const enabled = isScreenEnabled(activeModule.moduleId, screen.screenId);
      const isCollapsed = collapsedScreens.has(screen.screenId);
      return <section key={screen.screenId} className="border-b border-[#DCE4ED] last:border-b-0">
        <div className="flex flex-wrap items-center justify-between gap-4 px-6 py-4">
          <div><h3 className="text-[10px] font-semibold uppercase text-text-heading">{screen.screenName}</h3><p className="mt-1 text-[9px] text-text-secondary">{screen.screenCode}</p></div>
          <div className="flex items-center gap-2">
            <Toggle checked={enabled} label="Enable Screen" onClick={() => enabled ? clearScreen(activeModule.moduleId, screen.screenId) : grantScreen(activeModule, screen.screenId)} />
            <button type="button" onClick={() => grantScreen(activeModule, screen.screenId)} className="inline-flex h-8 items-center gap-2 rounded-[4px] bg-[#E7F1FD] px-3 text-[9px] font-semibold text-primary"><Check size={10} weight="bold" />Grant All Features</button>
            <button type="button" aria-label={`${isCollapsed ? "Expand" : "Collapse"} ${screen.screenName}`} aria-expanded={!isCollapsed} onClick={() => toggleScreenCollapsed(screen.screenId)} className="ml-2 grid h-7 w-7 place-items-center rounded-[4px] text-text-secondary transition-colors hover:bg-primary-light hover:text-primary">{isCollapsed ? <CaretDown size={12} /> : <CaretUp size={12} />}</button>
          </div>
        </div>
        {!isCollapsed ? <div className="border-t border-[#DCE4ED]">
          <div className="flex flex-wrap items-center gap-2 px-6 py-3">
            <span className="min-w-[220px] flex-1 text-[11px] font-medium text-primary">Screen Access</span>
            {actions.map((action) => <Toggle key={action} label={action.charAt(0) + action.slice(1).toLowerCase()} checked={selection[activeModule.moduleId]?.[screen.screenId]?.actions.includes(action) ?? false} onClick={() => toggle(activeModule.moduleId, screen.screenId, action)} />)}
          </div>
          {screen.features.map((feature) => <div key={feature.featureId} className="flex flex-wrap items-center gap-2 border-t border-[#E3E9F0] px-6 py-3"><span className="min-w-[220px] flex-1 text-[11px] font-medium text-primary">{feature.featureName}</span>{actions.map((action) => <Toggle key={action} label={action.charAt(0) + action.slice(1).toLowerCase()} checked={selection[activeModule.moduleId]?.[screen.screenId]?.features[feature.featureId]?.includes(action) ?? false} onClick={() => toggle(activeModule.moduleId, screen.screenId, action, feature.featureId)} />)}</div>)}
        </div> : null}
      </section>;
    })}</div>
    <div className="flex justify-end gap-3 border-t border-[#DCE4ED] px-6 py-5"><Button variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65" prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />} onClick={clearAll}>Clear All</Button><Button size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" isLoading={isSaving} onClick={save}>Save Permissions</Button></div>
    <Snackbar open={Boolean(message.text)} title={message.variant === "success" ? "Permissions updated" : "Unable to update permissions"} message={message.text} variant={message.variant} onClose={() => setMessage({ text: "", variant: "success" })} />
  </section>;
}
