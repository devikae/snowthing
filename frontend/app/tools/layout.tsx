import { Footer, TopNav } from "../components/SiteChrome";
import "./tools.css";

export default function ToolsLayout({ children }: { children: React.ReactNode }) {
  return <div className="community-page"><TopNav /><main className="community-container rider-tools">{children}</main><Footer /></div>;
}
