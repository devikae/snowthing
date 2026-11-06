import { Footer, TopNav } from "../components/SiteChrome";
import "./characterTest.css";

export default function CharacterTestLayout({ children }: { children: React.ReactNode }) {
  return <div className="community-page"><TopNav active="tools" /><main className="community-container character-test">{children}</main><Footer /></div>;
}
