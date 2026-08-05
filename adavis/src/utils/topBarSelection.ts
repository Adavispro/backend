export const TOP_BAR_SELECTION_EVENT = "adavis:top-bar-selection";

const TOP_BAR_SELECTION_PREFIX = "adavis:selected-equipment";

export function getTopBarSelectionKey(pathname: string) {
  return `${TOP_BAR_SELECTION_PREFIX}:${pathname}`;
}

export function readTopBarSelection(pathname: string) {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(getTopBarSelectionKey(pathname));
}

export function setTopBarSelection(pathname: string, value: string) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(getTopBarSelectionKey(pathname), value);
  window.dispatchEvent(
    new CustomEvent(TOP_BAR_SELECTION_EVENT, {
      detail: { pathname, value },
    }),
  );
}

export function clearTopBarSelection(pathname: string) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(getTopBarSelectionKey(pathname));
  window.dispatchEvent(
    new CustomEvent(TOP_BAR_SELECTION_EVENT, {
      detail: { pathname, value: null },
    }),
  );
}
