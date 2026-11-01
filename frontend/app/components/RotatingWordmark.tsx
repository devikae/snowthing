import type { CSSProperties } from "react";
import styles from "./rotatingWordmark.module.css";

function Word({ text, korean = false }: { text: string; korean?: boolean }) {
  return (
    <span className={`${styles.label} ${korean ? styles.korean : styles.english}`} data-word={korean ? "korean" : "english"}>
      {Array.from(text).map((glyph, index) => (
        <span className={styles.glyph} key={index}
          style={{ "--i": index } as CSSProperties}>
          <span className={styles.ink} data-word-ink>{glyph}</span>
        </span>
      ))}
    </span>
  );
}

export function RotatingWordmark() {
  return (
    <span className={styles.wordmark} aria-hidden="true">
      <Word text="SnowThing" />
      <Word text="눈팅" korean />
    </span>
  );
}
