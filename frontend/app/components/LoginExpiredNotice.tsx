// 글을 올리다 로그인이 풀렸을 때 쓰는 안내.
// 이 화면을 떠나면 입력과 사진이 사라지므로 로그인은 새 창에서 하고, 돌아와 같은 버튼을 다시 누르게 한다.
// guestAllowed: 익명게시판처럼 비회원도 쓸 수 있는 곳이면 비밀번호를 넣고 비회원으로 올리는 길도 함께 알려 준다.
export function LoginExpiredNotice({ returnUrl, action = "등록", guestAllowed = false }: { returnUrl: string; action?: string; guestAllowed?: boolean }) {
  return (
    <span>
      로그인이 풀렸습니다. 입력한 내용은 그대로 있습니다.{" "}
      {guestAllowed && <>비회원으로 올리려면 위 칸에 수정, 삭제 비밀번호를 넣고 다시 {action}해 주세요. 회원으로 올리려면 </>}
      <a className="underline" href={`/login?returnUrl=${encodeURIComponent(returnUrl)}`} target="_blank" rel="noopener">
        새 창에서 로그인
      </a>
      한 뒤 이 화면에서 다시 {action}해 주세요.
    </span>
  );
}
