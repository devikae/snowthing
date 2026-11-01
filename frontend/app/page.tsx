import type { Metadata } from "next";
import HomePageClient from "./HomePageClient";
import { SITE_URL, websiteStructuredData } from "./lib/siteMetadata";

export const metadata: Metadata = { alternates: { canonical: `${SITE_URL}/` } };

export default function HomePage() {
  return <>
    <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteStructuredData).replace(/</g, "\\u003c") }} />
    <HomePageClient />
  </>;
}
