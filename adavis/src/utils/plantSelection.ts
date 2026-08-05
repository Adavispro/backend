export const SELECTED_PLANT_STORAGE_KEY = "adavis:selected-plant-id";
export const SELECTED_PLANT_CHANGED_EVENT = "adavis:selected-plant-changed";
export const SELECTED_PLANT_HEADER = "X-Selected-Plant-Id";

export const readSelectedPlantId = () => {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(SELECTED_PLANT_STORAGE_KEY)?.trim() ?? "";
};

export const writeSelectedPlantId = (plantId: string) => {
  if (typeof window === "undefined") return;

  const normalized = plantId.trim();
  window.localStorage.setItem(SELECTED_PLANT_STORAGE_KEY, normalized);
  window.dispatchEvent(
    new CustomEvent(SELECTED_PLANT_CHANGED_EVENT, {
      detail: { plantId: normalized },
    }),
  );
};
