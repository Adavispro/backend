"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CaretDown } from "@phosphor-icons/react/dist/ssr";

export interface MultiSelectOption {
  label: string;
  value: string;
}

export interface MultiSelectDropdownProps {
  id?: string;
  disabled?: boolean;
  options?: MultiSelectOption[];
  placeholder: string;
  selectedValues: string[];
  onChange: (value: string[]) => void;
}

export default function MultiSelectDropdown({
  disabled,
  id,
  onChange,
  options = [],
  placeholder,
  selectedValues,
}: MultiSelectDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [dropdownPosition, setDropdownPosition] = useState({
    left: 0,
    maxHeight: 260,
    top: 0,
    width: 0,
  });
  const containerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const selectedOptions = useMemo(
    () => options.filter((option) => selectedValues.includes(option.value)),
    [options, selectedValues],
  );
  const displayText =
    selectedOptions.length === 0
      ? placeholder
      : selectedOptions.length <= 2
        ? selectedOptions.map((option) => option.label || option.value).join(", ")
        : `${selectedOptions.length} selected`;

  useEffect(() => {
    if (!isOpen) return;

    const updateDropdownPosition = () => {
      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) return;

      const availableBelow = window.innerHeight - rect.bottom - 12;
      setDropdownPosition({
        left: rect.left,
        maxHeight: Math.max(160, Math.min(280, availableBelow)),
        top: rect.bottom + 6,
        width: rect.width,
      });
    };

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        !containerRef.current?.contains(target) &&
        !dropdownRef.current?.contains(target)
      ) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsOpen(false);
    };

    updateDropdownPosition();
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", updateDropdownPosition);
    window.addEventListener("scroll", updateDropdownPosition, true);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", updateDropdownPosition);
      window.removeEventListener("scroll", updateDropdownPosition, true);
    };
  }, [isOpen]);

  const toggleValue = (value: string) => {
    if (!value) return;
    onChange(
      selectedValues.includes(value)
        ? selectedValues.filter((selectedValue) => selectedValue !== value)
        : [...selectedValues, value],
    );
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        id={id}
        type="button"
        disabled={disabled}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        onClick={() => setIsOpen((current) => !current)}
        className="module-glass-control type-filter-value flex h-9 w-full items-center justify-between rounded-[4px] px-3 text-left text-text-secondary outline-none transition-colors hover:bg-white/45 disabled:cursor-not-allowed disabled:opacity-70"
      >
        <span
          className={`min-w-0 flex-1 truncate ${
            selectedOptions.length > 0 ? "text-text-heading" : "text-text-secondary"
          }`}
        >
          {displayText}
        </span>
        <CaretDown
          aria-hidden="true"
          size={11}
          weight="bold"
          className={`ml-2 shrink-0 text-text-secondary transition-transform ${
            isOpen ? "rotate-180" : ""
          }`}
        />
      </button>

      {isOpen
        ? createPortal(
            <div
              ref={dropdownRef}
              role="listbox"
              aria-multiselectable="true"
              style={{
                left: dropdownPosition.left,
                maxHeight: dropdownPosition.maxHeight,
                top: dropdownPosition.top,
                width: dropdownPosition.width,
              }}
              className="fixed z-[220] overflow-auto rounded-[6px] border border-[#D8E3EF] bg-white p-1.5 shadow-[0_14px_30px_rgba(35,50,70,0.16)]"
            >
              {options.length ? (
                options.map((option) => {
                  const label = option.label || option.value;
                  const isSelected = selectedValues.includes(option.value);

                  return (
                    <button
                      key={`${option.value}-${label}`}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => toggleValue(option.value)}
                      className="flex w-full items-center gap-2 rounded-[4px] px-2.5 py-2 text-left text-[10px] font-medium text-text-heading transition-colors hover:bg-primary-light"
                    >
                      <span
                        aria-hidden="true"
                        className={`grid h-3.5 w-3.5 shrink-0 place-items-center rounded-[3px] border ${
                          isSelected
                            ? "border-primary bg-primary"
                            : "border-[#B9C9DA] bg-white"
                        }`}
                      >
                        {isSelected ? (
                          <span className="h-1.5 w-1.5 rounded-[1px] bg-white" />
                        ) : null}
                      </span>
                      <span className="min-w-0 flex-1 truncate">{label}</span>
                    </button>
                  );
                })
              ) : (
                <div className="px-2.5 py-2 text-[10px] text-text-secondary">
                  No options available.
                </div>
              )}
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}
