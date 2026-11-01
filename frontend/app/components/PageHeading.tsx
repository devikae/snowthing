import type { ReactNode } from "react";
import styles from "./pageHeading.module.css";

interface PageHeadingProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  meta?: ReactNode;
  breadcrumb?: ReactNode;
}

export function PageHeading({ title, description, actions, meta, breadcrumb }: PageHeadingProps) {
  return (
    <header className={styles.heading}>
      <div className={styles.content}>
        {breadcrumb && <div className={styles.breadcrumb}>{breadcrumb}</div>}
        <div className={styles.titleRow}><h1>{title}</h1>{meta}</div>
        {description && <p className={styles.description}>{description}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  );
}
