// 글을 올리다 로그인이 풀렸을 때 쓰는 안내.
// 이 화면을 떠나면 입력과 사진이 사라지므로 로그인은 새 창에서 하고, 돌아와 같은 버튼을 다시 누르게 한다.
export function LoginExpiredNotice({ returnUrl, action = "등록" }: { returnUrl: string; action?: string }) {
  return (
    <span>
      로그인이 풀렸습니다. 입력한 내용은 그대로 있습니다.{" "}
      <a className="underline" href={`/login?returnUrl=${encodeURIComponent(returnUrl)}`} target="_blank" rel="noopener">
        새 창에서 로그인
      </a>
      한 뒤 이 화면에서 다시 {action}해 주세요.
    </span>
  );
}
