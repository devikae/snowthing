import type { MetadataRoute } from "next";
import { SITE_DESCRIPTION } from "./lib/siteMetadata";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "눈팅 SnowThing",
    short_name: "눈팅",
    description: SITE_DESCRIPTION,
    start_url: "/",
    display: "browser",
    background_color: "#ffffff",
    theme_color: "#111111",
    lang: "ko",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml" },
      { src: "/apple-icon.png", sizes: "180x180", type: "image/png" },
    ],
  };
}
