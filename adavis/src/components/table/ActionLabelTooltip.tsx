"use client";

import type { ReactNode } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

interface ActionLabelTooltipProps {
  label: string;
  children: ReactNode;
}

export default function ActionLabelTooltip({
  label,
  children,
}: ActionLabelTooltipProps) {
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ left: 0, top: 0 });

  const updatePosition = useCallback(() => {
    const bounds = wrapperRef.current?.getBoundingClientRect();
    if (!bounds) return;

    setPosition({
      left: bounds.left + bounds.width / 2,
      top: bounds.top - 8,
    });
  }, []);

  const openTooltip = () => {
    updatePosition();
    setIsOpen(true);
  };

  useEffect(() => {
    if (!isOpen) return;

    window.addEventListener("scroll", updatePosition, true);
    window.addEventListener("resize", updatePosition);

    return () => {
      window.removeEventListener("scroll", updatePosition, true);
      window.removeEventListener("resize", updatePosition);
    };
  }, [isOpen, updatePosition]);

  return (
    <span
      ref={wrapperRef}
      className="inline-flex"
      onMouseEnter={openTooltip}
      onMouseLeave={() => setIsOpen(false)}
      onFocus={openTooltip}
      onBlur={() => setIsOpen(false)}
    >
      {children}

      {isOpen && typeof document !== "undefined"
        ? createPortal(
            <span
              role="tooltip"
              className="pointer-events-none fixed z-[9999] -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-md bg-[#111827]/85 px-2.5 py-1.5 text-[10px] font-semibold leading-none text-white shadow-[0_8px_18px_rgba(15,23,42,0.2)] backdrop-blur-sm"
              style={{
                left: position.left,
                top: position.top,
              }}
            >
              {label}
              <span className="absolute left-1/2 top-full h-1.5 w-1.5 -translate-x-1/2 -translate-y-0.5 rotate-45 bg-[#111827]/85" />
            </span>,
            document.body,
          )
        : null}
    </span>
  );
}
