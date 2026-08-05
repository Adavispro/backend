"use client";

import { useCallback, useEffect, useState } from "react";
import {
  getCriticalParameterLimits,
  getCriticalParameters,
  getIiotAssets,
  getProductMasters,
  isMutableIiotMasterSection,
  setIiotMasterRecordActive,
  updateCriticalParameter,
  updateCriticalParameterLimit,
  updateIiotAsset,
  updateProductMaster,
} from "../api";
import type {
  CriticalParameter,
  CriticalParameterLimit,
  IiotAsset,
  IiotMasterRecord,
  IiotMasterSection,
  ProductMaster,
  UpdateCriticalParameterLimitValues,
  UpdateCriticalParameterValues,
  UpdateIiotAssetValues,
  UpdateProductMasterValues,
} from "../api";

interface IiotMasterState {
  equipments: IiotAsset[];
  "critical-parameters": CriticalParameter[];
  "critical-parameter-limits": CriticalParameterLimit[];
  "product-master": ProductMaster[];
}

type MutableIiotMasterRecord =
  | IiotAsset
  | CriticalParameter
  | CriticalParameterLimit
  | ProductMaster;

type IiotMasterUpdateValues =
  | UpdateIiotAssetValues
  | UpdateCriticalParameterValues
  | UpdateCriticalParameterLimitValues
  | UpdateProductMasterValues;

const initialState: IiotMasterState = {
  equipments: [],
  "critical-parameters": [],
  "critical-parameter-limits": [],
  "product-master": [],
};

export function useIiotMasterData() {
  const [records, setRecords] = useState<IiotMasterState>(initialState);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const reload = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    try {
      const results = await Promise.allSettled([
        getIiotAssets(signal),
        getCriticalParameters(signal),
        getCriticalParameterLimits(signal),
        getProductMasters(signal),
      ]);

      const nextState: IiotMasterState = {
        equipments:
          results[0].status === "fulfilled" ? results[0].value : [],
        "critical-parameters":
          results[1].status === "fulfilled" ? results[1].value : [],
        "critical-parameter-limits":
          results[2].status === "fulfilled" ? results[2].value : [],
        "product-master":
          results[3].status === "fulfilled" ? results[3].value : [],
      };

      setRecords(nextState);

      const failedSections = [
        results[0].status === "rejected" ? "equipments" : null,
        results[1].status === "rejected" ? "critical parameters" : null,
        results[2].status === "rejected" ? "parameter limits" : null,
        results[3].status === "rejected" ? "product master" : null,
      ].filter(Boolean);

      if (failedSections.length > 0) {
        setErrorMessage(
          `Some IIOT master sections failed to load: ${failedSections.join(", ")}.`,
        );
      } else {
        setErrorMessage("");
      }
    } catch (error) {
      if (!signal?.aborted) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "Unable to load IIOT master data.",
        );
      }
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void Promise.resolve().then(() => reload(controller.signal));
    return () => controller.abort();
  }, [reload]);

  const replaceRecord = (
    section: IiotMasterSection,
    updated: IiotMasterRecord,
  ) => {
    setRecords((current) => {
      const idFields = {
        equipments: "equipmentId",
        "critical-parameters": "parameterId",
        "critical-parameter-limits": "parameterLimitId",
        "product-master": "productId",
      } as const;
      const idField = idFields[section];
      const updatedId = String(updated[idField as keyof IiotMasterRecord]);

      return {
        ...current,
        [section]: current[section].map((item) =>
          String(item[idField as keyof typeof item]) === updatedId
            ? updated
            : item,
        ),
      } as IiotMasterState;
    });
  };

  const changeStatus = async (
    section: IiotMasterSection,
    record: IiotMasterRecord,
    active: boolean,
  ) => {
    if (!isMutableIiotMasterSection(section)) return;
    const updated = await setIiotMasterRecordActive(
      section,
      record as MutableIiotMasterRecord,
      active,
    );
    replaceRecord(section, updated);
  };

  const updateRecord = async (
    section: IiotMasterSection,
    record: IiotMasterRecord,
    values: IiotMasterUpdateValues,
  ) => {
    const currentActive =
      "isActive" in record ? Boolean(record.isActive) : true;
    const nextActive =
      "isActive" in values ? Boolean(values.isActive) : currentActive;
    const editableValues = Object.fromEntries(
      Object.entries(values).filter(([key]) => key !== "isActive"),
    );
    const hasEditableChanges = Object.entries(editableValues).some(
      ([key, value]) =>
        String((record as Record<string, unknown>)[key] ?? "") !==
        String(value ?? ""),
    );

    let workingRecord = record as MutableIiotMasterRecord;

    if (!currentActive && (nextActive || hasEditableChanges)) {
      workingRecord = await setIiotMasterRecordActive(
        section,
        workingRecord,
        true,
      );
    }

    if (hasEditableChanges) {
      const updated = await updateEditableIiotMasterFields(
        section,
        workingRecord,
        editableValues,
      );
      workingRecord = updated as MutableIiotMasterRecord;
    }

    if (!nextActive && Boolean(workingRecord.isActive)) {
      workingRecord = await setIiotMasterRecordActive(
        section,
        workingRecord,
        false,
      );
    }

    if (
      currentActive === nextActive &&
      !hasEditableChanges &&
      workingRecord === record
    ) {
      return record;
    }

    replaceRecord(section, workingRecord);
    return workingRecord;
  };

  const updateEditableIiotMasterFields = async (
    section: IiotMasterSection,
    record: MutableIiotMasterRecord,
    values: Record<string, unknown>,
  ) => {
    if (section === "equipments" && "equipmentId" in record) {
      const updated = await updateIiotAsset(
        String(record.equipmentId),
        values as UpdateIiotAssetValues,
      );
      return updated;
    }

    if (section === "critical-parameters" && "parameterId" in record) {
      const updated = await updateCriticalParameter(
        String(record.parameterId),
        values as UpdateCriticalParameterValues,
      );
      return updated;
    }

    if (section === "critical-parameter-limits" && "parameterLimitId" in record) {
      const updated = await updateCriticalParameterLimit(
        String(record.parameterLimitId),
        values as UpdateCriticalParameterLimitValues,
      );
      return updated;
    }

    if (section === "product-master" && "productId" in record) {
      const updated = await updateProductMaster(
        String(record.productId),
        values as UpdateProductMasterValues,
      );
      return updated;
    }

    throw new Error("This IIOT master record cannot be updated.");
  };

  return {
    clearError: () => setErrorMessage(""),
    changeStatus,
    errorMessage,
    isLoading,
    records,
    reload,
    updateRecord,
  };
}
