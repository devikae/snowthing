import { Footer, TopNav } from "../components/SiteChrome";
import type { termsDocument } from "../lib/registrationPolicy";

export default function PrivacyDocument({policy}:{policy:typeof termsDocument}) {
  return <div className="min-h-screen bg-[var(--snow-background)] text-[var(--snow-ink)]">
    <TopNav />
    <main className="mx-auto w-full max-w-3xl px-5 py-10">
      <article className="snow-card grid gap-6 p-6 leading-7 md:p-8">
        <h1 className="text-2xl font-bold">{policy.title}</h1>
        <p className="text-sm text-[var(--snow-muted)]">문서 버전: {policy.version}</p>
        <p>{policy.intro}</p>
        {policy.sections.map(section=><section key={section.title}>
          <h2 className="text-lg font-bold">{section.title}</h2>
          {section.paragraphs.map(text=><p key={text}>{text}</p>)}
        </section>)}
        <a className="text-[var(--alpine-sky)] underline" href={`mailto:${policy.contact}`}>{policy.contact}</a>
        <p className="text-sm text-[var(--snow-muted)]">{policy.notice}</p>
      </article>
    </main><Footer />
  </div>;
}
