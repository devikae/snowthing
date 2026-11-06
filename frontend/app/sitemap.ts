import type { MetadataRoute } from "next";
import { SITE_URL } from "./lib/siteMetadata";
import { getResortCameras } from "./resort-cam/data";
import { resortSlug } from "./resort-cam/types";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const resorts = await getResortCameras();
  const paths = ["/", "/posts", "/carpool", "/resort-reports", "/resort-cam", "/tools", "/tools/season-pass", "/tools/deck-size", "/character-test", ...resorts.map(resort => `/resort-cam/${resortSlug(resort.code)}`)];
  return paths.map(path => ({ url: new URL(path, SITE_URL).toString() }));
}
