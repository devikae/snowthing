import { Footer, TopNav } from "../components/SiteChrome";
import { getResortCameras } from "./data";
import { ResortCamProvider } from "./ResortCamProvider";

export const dynamic = "force-dynamic";

export default async function ResortCamLayout({ children }: { children: React.ReactNode }) {
  const resorts = await getResortCameras();

  return (
    <div className="community-page">
      <TopNav active="resort" />
      <ResortCamProvider resorts={resorts}>{children}</ResortCamProvider>
      <Footer />
    </div>
  );
}
