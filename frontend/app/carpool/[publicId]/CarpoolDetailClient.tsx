"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthorMenu } from "../../components/AuthorMenu";
import { CommentProfileAvatar } from "../../components/CommentProfileAvatar";
import PostComments from "../../components/PostComments";
import { Footer, TopNav } from "../../components/SiteChrome";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { errorText } from "../../lib/errorText";
import { fetchViewer, isAdminViewer } from "../../lib/viewer";
import { ReportButton } from "../../components/ReportButton";
import KakaoShareButton from "../../components/KakaoShareButton";
import { carpoolCostLabel, formatCarpoolDateTime } from "../format";
import { carpoolSummaryText, type CarpoolReviewSummary } from "../review";
import { CarpoolParticipation } from "./CarpoolParticipation";
import { readErrorMessage } from "../../lib/http";

interface CarpoolDetail {
  publicId: string;
  title: string;
  content: string;
  writerPublicId: string | null;
  writerName: string;
  writerProfileImageUrl: string | null;
  departureRegion: string;
  meetingPlace: string;
  destinationResortName: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  departureAt: string;
  returnAt: string | null;
  passengerCapacity: number;
  equipmentLoadAvailable: boolean;
  costMode: "AUTO" | "MANUAL";
  fuelPrice: number | null;
  fuelPriceSource: "OPINET" | "CACHE" | "USER_INPUT" | null;
  fuelPriceObservedAt: string | null;
  routeDistanceKm: number | null;
  routeTollFee: number | null;
  routeSource: "KAKAO" | "MANUAL" | null;
  estimatedFuelCost: number | null;
  estimatedTotalCost: number | null;
  estimatedCostPerPerson: number;
  contactInfo: string | null;
  contactPublicToGuest: boolean;
  canManage: boolean;
  status: string;
  createdAt: string;
}

const formatNumber = (value: number | null) => (value ?? 0).toLocaleString("ko-KR");

function fuelPriceNote(item: CarpoolDetail): string {
  const source =
    item.fuelPriceSource === "CACHE"
      ? "오피넷 유효 캐시"
      : item.fuelPriceSource === "USER_INPUT"
        ? "유가 사용자 직접 입력"
        : "오피넷 유가";
  const observedAt = item.fuelPriceObservedAt
    ? ` ${formatCarpoolDateTime(item.fuelPriceObservedAt)} 기준`
    : "";
  const route = item.routeSource === "MANUAL" ? "경로와 통행료 사용자 직접 입력, " : "";
  return `${route}${source}${observedAt}`;
}

export default function CarpoolDetailClient() {
  const { publicId } = useParams<{ publicId: string }>();
  const router = useRouter();
  const [item, setItem] = useState<CarpoolDetail | null>(null);
  const [error, setError] = useState(false);
  const [heldMessage, setHeldMessage] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [writerSummary, setWriterSummary] = useState("");
  const [isAdmin, setIsAdmin] = useState(false);
  const [blindBusy, setBlindBusy] = useState(false);
  const writerPublicId = item?.writerPublicId ?? null;

  // 작성자의 공개 카풀 요약. 실패하거나 기록이 없으면 아무것도 표시하지 않는다.
  useEffect(() => {
    if (!writerPublicId) return;
    let cancelled = false;
    void fetch(API_ENDPOINTS.carpool.reviewSummary(writerPublicId), { credentials: "include" })
      .then((response) => (response.ok ? (response.json() as Promise<CarpoolReviewSummary>) : null))
      .then((summary) => {
        if (!cancelled) setWriterSummary(carpoolSummaryText(summary));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [writerPublicId]);

  useEffect(() => {
    let cancelled = false;
    void fetchViewer().then((viewer) => {
      if (!cancelled) setIsAdmin(isAdminViewer(viewer));
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // 관리자가 모집글 화면에서 바로 가리거나 가리기를 푼다. 권한은 서버의 관리자 API가 다시 확인한다.
  const toggleBlind = async () => {
    if (!item || blindBusy) return;
    const blinded = item.status !== "BLOCKED";
    if (!window.confirm(blinded ? "이 글을 가릴까요? 일반 사용자는 목록과 상세 화면에서 볼 수 없게 됩니다." : "이 글의 가리기를 해제할까요?")) return;
    setBlindBusy(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.blindPost(item.publicId), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ blinded }),
      });
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "가리기 상태를 바꾸지 못했습니다."));
      }
      setItem((current) => (current ? { ...current, status: blinded ? "BLOCKED" : "NORMAL" } : current));
    } catch (blindError) {
      window.alert(errorText(blindError, "서버에 연결하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요."));
    } finally {
      setBlindBusy(false);
    }
  };

  const deleteCarpool = async () => {
    if (!window.confirm("이 카풀 모집글을 삭제할까요?")) return;
    setDeleting(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.carpool.delete(publicId), {
        method: "DELETE",
      });
      if (!response.ok) throw new Error();
      router.replace("/carpool");
    } catch {
      window.alert("모집글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      setDeleting(false);
    }
  };

  useEffect(() => {
    void fetch(API_ENDPOINTS.carpool.detail(publicId), { credentials: "include" })
      .then(async (response) => {
        if (response.status === 404) {
          setNotFound(true);
          return null;
        }
        if (!response.ok) {
          const body = response.status === 403 ? await response.json().catch(() => null) : null;
          if (body?.code === "POST_009") setHeldMessage(body.message);
          throw new Error();
        }
        return response.json();
      })
      .then((data) => {
        if (data) {
          setItem(data);
          document.title = `${data.title} | 눈팅 SnowThing`;
        }
      })
      .catch(() => setError(true));
  }, [publicId]);

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container carpool-detail-page content-detail-page">
        {notFound ? (
          <div className="carpool-empty">
            <p>모집글을 찾을 수 없거나 삭제되었습니다.</p>
            <Link href="/carpool">목록으로 돌아가기</Link>
          </div>
        ) : error ? (
          <div className="carpool-empty">
            <p>{heldMessage ?? "모집글을 불러오지 못했습니다."}</p>
            {heldMessage ? <Link href="/carpool">목록으로 돌아가기</Link> : <button type="button" onClick={() => window.location.reload()}>다시 시도</button>}
          </div>
        ) : !item ? (
          <div className="carpool-empty">모집글을 불러오는 중입니다.</div>
        ) : (
          <>
            <div className="post-detail-toolbar">
              <Link href="/carpool"><span className="material-symbols-outlined" aria-hidden="true">arrow_back</span> 목록으로 돌아가기</Link>
              <div>
                <KakaoShareButton
                  title={item.title}
                  description={`${item.departureRegion} 출발, ${item.destinationResortName}, ${formatCarpoolDateTime(item.departureAt)}`}
                  url={`/carpool/${item.publicId}`}
                  buttonTitle="카풀 보기"
                />
              </div>
            </div>
            <div className="carpool-view">
              <header className="carpool-view-head">
                <div className="carpool-view-badges">
                  <span className="carpool-view-badge solid">{item.tripType === "ROUND_TRIP" ? "왕복" : "편도"}</span>
                  <span className={`carpool-view-badge ${item.equipmentLoadAvailable ? "outline" : "muted"}`}>
                    {item.equipmentLoadAvailable ? "장비 적재 가능" : "장비 적재 불가"}
                  </span>
                </div>
                {isAdmin && item.status === "BLOCKED" && (
                  <p role="status" className="mt-3 rounded border border-[var(--snow-border)] px-3 py-2 text-sm text-[var(--snow-muted)]">
                    가린 글입니다. 일반 사용자에게는 보이지 않고 관리자에게만 보입니다.
                  </p>
                )}
                {isAdmin && item.status === "TEMPORARY_HOLD" && (
                  <p role="status" className="mt-3 rounded border border-[var(--snow-border)] px-3 py-2 text-sm text-[var(--snow-muted)]">
                    신고로 임시조치된 글입니다. 해제는 신고 처리 화면에서 합니다.
                  </p>
                )}
                <h1>{item.title}</h1>
                <div className="post-detail-author">
                  <CommentProfileAvatar
                    className="post-detail-author-avatar"
                    nickname={item.writerName}
                    profileImageUrl={item.writerProfileImageUrl}
                    size={32}
                  />
                  <div>
                    <AuthorMenu publicId={item.writerPublicId} nickname={item.writerName} />
                    <small>{formatCarpoolDateTime(item.createdAt)}</small>
                    {writerSummary && <small className="carpool-writer-summary">{writerSummary}</small>}
                  </div>
                  <ReportButton targetType="POST" targetId={publicId} />
                  {(() => {
                    // 가린 글은 서버가 수정과 삭제를 막으므로 공개 상태에서만 버튼을 보인다.
                    const canManage = item.canManage && item.status === "NORMAL";
                    const canBlind = isAdmin && (item.status === "NORMAL" || item.status === "BLOCKED");
                    if (!canManage && !canBlind) return null;
                    return (
                      <div className="post-detail-owner-actions">
                        {canManage && <Link href={`/carpool/${publicId}/edit`}>수정</Link>}
                        {canBlind && (
                          <button type="button" disabled={blindBusy} onClick={() => void toggleBlind()}>
                            {item.status === "BLOCKED" ? "가리기 해제" : "가리기"}
                          </button>
                        )}
                        {canManage && (
                          <button type="button" disabled={deleting} onClick={() => void deleteCarpool()}>
                            {deleting ? "삭제 중" : "삭제"}
                          </button>
                        )}
                      </div>
                    );
                  })()}
                </div>
              </header>

              <aside className="carpool-view-side">
                <section className="carpool-view-card" aria-label="비용">
                  <small>{carpoolCostLabel(item.costMode)}</small>
                  <p className="carpool-view-price">
                    {item.estimatedCostPerPerson.toLocaleString("ko-KR")}<span>원</span>
                  </p>
                  <dl className="carpool-view-rows">
                    <div><dt>모집 인원</dt><dd>{item.passengerCapacity}명</dd></div>
                    {item.costMode === "AUTO" && (
                      <>
                        <div><dt>{item.routeSource === "MANUAL" ? "직접 입력 거리" : "이동 거리"}</dt><dd>{formatNumber(item.routeDistanceKm)}km</dd></div>
                        <div><dt>통행료</dt><dd>{formatNumber(item.routeTollFee)}원</dd></div>
                        <div><dt>{item.fuelPriceSource === "USER_INPUT" ? "사용자 입력 유가" : "기준 유가"}</dt><dd>{formatNumber(item.fuelPrice)}원/L</dd></div>
                        <div><dt>연료비</dt><dd>{formatNumber(item.estimatedFuelCost)}원</dd></div>
                        <div><dt>예상 총액</dt><dd>{formatNumber(item.estimatedTotalCost)}원</dd></div>
                      </>
                    )}
                  </dl>
                  {item.costMode === "AUTO" && <p className="carpool-view-note">{fuelPriceNote(item)}</p>}
                </section>
                {item.contactInfo && (
                  <section className="carpool-view-card">
                    <small>연락 방법</small>
                    <strong className="carpool-view-contact">{item.contactInfo}</strong>
                  </section>
                )}
              </aside>

              <div className="carpool-view-body">
                <section className="carpool-view-route" aria-label="이동 경로와 일정">
                  <div className="carpool-view-stop start">
                    <small>출발</small>
                    <strong>{item.departureRegion}</strong>
                    <span>{item.meetingPlace}, {formatCarpoolDateTime(item.departureAt)}</span>
                  </div>
                  <div className="carpool-view-stop end">
                    <small>도착</small>
                    <strong>{item.destinationResortName}</strong>
                    {item.returnAt && <span>복귀 {formatCarpoolDateTime(item.returnAt)}</span>}
                  </div>
                </section>
                <section className="carpool-view-content">
                  <h2>모집 내용</h2>
                  <p>{item.content}</p>
                </section>
                {item.status === "NORMAL" && <CarpoolParticipation publicId={publicId} />}
                <PostComments postPublicId={publicId} />
              </div>
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
