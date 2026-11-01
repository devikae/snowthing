import type { MetadataRoute } from "next";
import { SITE_URL } from "./lib/siteMetadata";

export default function robots(): MetadataRoute.Robots {
  return { rules: { userAgent: "*", allow: "/", disallow: ["/stream-proxy/"] }, sitemap: `${SITE_URL}/sitemap.xml` };
}
