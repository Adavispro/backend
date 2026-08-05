"use client";

import type { ReactNode } from "react";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CaretRight } from "@phosphor-icons/react";

const ACTION_TOOLTIP_OPEN_EVENT = "adavis:action-tooltip-open";

export interface ActionTooltipOption {
  label: string;
  onClick?: () => void;
  href?: string;
}

export interface ActionTooltipProps {
  trigger: ReactNode;
  options: ActionTooltipOption[];
  ariaLabel?: string;
  className?: string;
}

export default function ActionTooltip({
  trigger,
  options,
  ariaLabel = "Open action menu",
  className = "",
}: ActionTooltipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ left: 0, top: 0 });
  const tooltipId = useId();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const closeTimeoutRef = useRef<number | null>(null);
  const menuWidth = 128;

  const updatePosition = useCallback(() => {
    const triggerBounds = wrapperRef.current?.getBoundingClientRect();
    if (!triggerBounds) return;

    const viewportPadding = 12;
    const centeredLeft = triggerBounds.left + triggerBounds.width / 2;
    const minLeft = viewportPadding + menuWidth / 2;
    const maxLeft = window.innerWidth - viewportPadding - menuWidth / 2;

    setPosition({
      left: Math.min(Math.max(centeredLeft, minLeft), maxLeft),
      top: triggerBounds.bottom + 14,
    });
  }, []);

  const clearCloseTimeout = useCallback(() => {
    if (closeTimeoutRef.current) {
      window.clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
  }, []);

  const closeMenu = useCallback(() => {
    clearCloseTimeout();
    setIsOpen(false);
  }, [clearCloseTimeout]);

  const scheduleClose = () => {
    clearCloseTimeout();
    closeTimeoutRef.current = window.setTimeout(() => {
      setIsOpen(false);
      closeTimeoutRef.current = null;
    }, 120);
  };

  const openMenu = () => {
    clearCloseTimeout();
    updatePosition();
    setIsOpen(true);
    window.dispatchEvent(
      new CustomEvent(ACTION_TOOLTIP_OPEN_EVENT, {
        detail: { id: tooltipId },
      }),
    );
  };

  useEffect(() => {
    const handleAnotherTooltipOpen = (event: Event) => {
      const openedId = (event as CustomEvent<{ id: string }>).detail?.id;
      if (openedId !== tooltipId) {
        setIsOpen(false);
      }
    };

    window.addEventListener(
      ACTION_TOOLTIP_OPEN_EVENT,
      handleAnotherTooltipOpen,
    );

    return () => {
      window.removeEventListener(
        ACTION_TOOLTIP_OPEN_EVENT,
        handleAnotherTooltipOpen,
      );
      clearCloseTimeout();
    };
  }, [clearCloseTimeout, tooltipId]);

  useEffect(() => {
    if (!isOpen) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(target) &&
        menuRef.current &&
        !menuRef.current.contains(target)
      ) {
        closeMenu();
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeMenu();
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", updatePosition);
    window.addEventListener("scroll", updatePosition, true);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", updatePosition);
      window.removeEventListener("scroll", updatePosition, true);
    };
  }, [closeMenu, isOpen, updatePosition]);

  return (
    <div
      ref={wrapperRef}
      className={`relative inline-flex ${className}`}
      onMouseEnter={openMenu}
      onMouseLeave={scheduleClose}
    >
      <button
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        onClick={() => {
          if (isOpen) {
            closeMenu();
            return;
          }
          openMenu();
        }}
        className="contents"
      >
        {trigger}
      </button>

      {isOpen && typeof document !== "undefined"
        ? createPortal(
        <div
          ref={menuRef}
          role="menu"
          onMouseEnter={clearCloseTimeout}
          onMouseLeave={scheduleClose}
          className="fixed z-[120] w-32 -translate-x-1/2 rounded-md border border-[#F0EAEA] bg-white shadow-[0_8px_18px_rgba(36,48,66,0.11)]"
          style={{ left: position.left, top: position.top }}
        >
          <span
            aria-hidden="true"
            className="absolute -top-2 left-1/2 h-4 w-4 -translate-x-1/2 rotate-45 border-l border-t border-[#F0EAEA] bg-white"
          />
          <div className="relative overflow-hidden rounded-md bg-white">
            {options.map((option, index) => (
              <button
                key={option.label}
                type="button"
                role="menuitem"
                onClick={() => {
                  option.onClick?.();
                  if (option.href) window.location.href = option.href;
                  closeMenu();
                }}
                className={`flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-[9px] font-semibold leading-tight text-primary transition-colors hover:bg-[#F6FAFF] ${
                  index > 0 ? "border-t border-[#E6E6E6]" : ""
                }`}
              >
                <span>{option.label}</span>
                <CaretRight size={9} weight="bold" />
              </button>
            ))}
          </div>
        </div>,
          document.body,
        )
        : null}
    </div>
  );
}
