import { Footer, TopNav } from "../components/SiteChrome";

export default function CatalogLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="community-page">
      <TopNav />
      {children}
      <Footer />
    </div>
  );
}
