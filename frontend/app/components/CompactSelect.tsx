"use client";

import { useEffect, useId, useRef, useState } from "react";

export interface CompactSelectOption {
  value: string;
  label: string;
}

interface CompactSelectProps {
  value: string;
  options: CompactSelectOption[];
  onChange: (value: string) => void;
  ariaLabel: string;
  disabled?: boolean;
  className?: string;
}

export function CompactSelect({
  value,
  options,
  onChange,
  ariaLabel,
  disabled = false,
  className = "",
}: CompactSelectProps) {
  const listboxId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const selectedIndex = Math.max(0, options.findIndex((option) => option.value === value));
  const [activeIndex, setActiveIndex] = useState(selectedIndex);
  const selectedOption = options[selectedIndex];

  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, []);

  const openList = () => {
    if (disabled || options.length === 0) return;
    setActiveIndex(selectedIndex);
    setOpen(true);
  };

  const selectAt = (index: number) => {
    const option = options[index];
    if (!option) return;
    onChange(option.value);
    setActiveIndex(index);
    setOpen(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (disabled || options.length === 0) return;
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (!open) {
        openList();
        return;
      }
      const direction = event.key === "ArrowDown" ? 1 : -1;
      setActiveIndex((current) => (current + direction + options.length) % options.length);
      return;
    }
    if ((event.key === "Enter" || event.key === " ") && open) {
      event.preventDefault();
      selectAt(activeIndex);
    }
  };

  return (
    <div ref={rootRef} className={`compact-select ${className}`.trim()}>
      <button
        type="button"
        className="compact-select-trigger"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        disabled={disabled || options.length === 0}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={handleKeyDown}
      >
        <span>{selectedOption?.label ?? "선택"}</span>
        <span className="material-symbols-outlined" aria-hidden="true">expand_more</span>
      </button>
      {open && (
        <div id={listboxId} className="compact-select-list" role="listbox" aria-label={ariaLabel}>
          {options.map((option, index) => (
            <button
              type="button"
              role="option"
              aria-selected={option.value === value}
              className={`${option.value === value ? "selected" : ""} ${index === activeIndex ? "active" : ""}`.trim()}
              key={option.value}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => selectAt(index)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
