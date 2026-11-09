import { Footer, TopNav } from "../components/SiteChrome";
import "../tools/tools.css";
import "./seasonLog.css";

export default function SeasonLogLayout({ children }: { children: React.ReactNode }) {
  return <div className="community-page"><TopNav active="tools" /><main className="community-container rider-tools season-log">{children}</main><Footer /></div>;
}
