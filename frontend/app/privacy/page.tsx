import { Footer, TopNav } from "../components/SiteChrome";

export default function PrivacyPage() {
  return (
    <div className="min-h-screen bg-[var(--snow-background)] text-[var(--snow-ink)]">
      <TopNav />
      <main className="snow-container mx-auto max-w-3xl px-5 py-10">
        <article className="snow-card grid gap-6 p-6 leading-7 md:p-8">
          <h1 className="text-2xl font-bold">개인정보처리방침</h1>
          <p>SnowThing은 서비스 제공과 이용자 문의 처리를 위해 개인정보를 처리합니다.</p>
          <section><h2 className="text-lg font-bold">처리하는 개인정보와 목적</h2><p>회원가입과 계정 관리에 이메일, 비밀번호, 닉네임 및 이메일 인증 정보를 사용합니다. 비밀번호는 해시 처리하여 저장합니다. 소셜 로그인 이용 시 해당 서비스에서 제공하는 계정 식별자와 이메일 등을 사용합니다.</p><p>서비스 이용 과정에서 이용자가 입력한 프로필, 게시글, 댓글, 사진과 거래 연락처, 접속 관련 정보가 처리될 수 있습니다.</p></section>
          <section><h2 className="text-lg font-bold">보관과 삭제</h2><p>이메일 인증 정보는 인증 유효기간이 지난 뒤 24시간이 경과한 건을 매일 정리하도록 구성되어 있습니다.</p><p>회원정보, 게시글, 이미지, 접속 기록 등 나머지 정보의 보관 기간과 구체적인 파기 방법은 확인 중입니다. 개인정보 관련 삭제 요청은 아래 이메일로 문의할 수 있습니다.</p></section>
          <section><h2 className="text-lg font-bold">개인정보 처리에 사용하는 서비스</h2><p>설계 문서에 따르면 이미지 저장·전송에는 Amazon S3 및 CloudFront, 인증 메일 발송에는 Amazon SES를 사용합니다. 실제 운영 위탁 현황과 국외 이전 여부는 확인 중입니다.</p></section>
          <section><h2 className="text-lg font-bold">개인정보 문의</h2><p>개인정보 열람·정정·삭제 등 관련 요청과 문의는 SnowThing 운영팀으로 보내 주세요.</p><a className="text-sky-600 underline" href="mailto:82snowthing@gmail.com">82snowthing@gmail.com</a></section>
          <p className="text-sm text-[var(--snow-muted)]">이 문서는 처리 현황을 확인 중인 초안입니다.</p>
        </article>
      </main>
      <Footer />
    </div>
  );
}
