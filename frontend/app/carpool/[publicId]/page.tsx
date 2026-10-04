"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import PostComments from "../../components/PostComments";
import { Footer, TopNav } from "../../components/SiteChrome";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";

interface CarpoolDetail {
  publicId: string;
  title: string;
  content: string;
  writerName: string;
  departureRegion: string;
  meetingPlace: string;
  destinationResortName: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  departureAt: string;
  returnAt: string | null;
  passengerCapacity: number;
  equipmentLoadAvailable: boolean;
  fuelPrice: number;
  fuelPriceSource: "OPINET" | "CACHE" | "USER_INPUT";
  fuelPriceObservedAt: string;
  routeDistanceKm: number;
  routeTollFee: number;
  routeSource: "KAKAO" | "MANUAL";
  estimatedFuelCost: number;
  estimatedTotalCost: number;
  estimatedCostPerPerson: number;
  contactInfo: string | null;
  contactPublicToGuest: boolean;
  canManage: boolean;
}

export default function CarpoolDetailPage() {
  const { publicId } = useParams<{ publicId: string }>();
  const router = useRouter();
  const [item, setItem] = useState<CarpoolDetail | null>(null);
  const [error, setError] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const deleteCarpool = async () => {
    if (!window.confirm("이 카풀 모집글을 삭제하시겠습니까?")) return;
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
      .then((response) => {
        if (!response.ok) throw new Error();
        return response.json();
      })
      .then(setItem)
      .catch(() => setError(true));
  }, [publicId]);

  return <>
    <TopNav active="carpool" />
    <main className="community-container carpool-detail-page">
      {error ? <div className="carpool-empty">모집글을 불러오지 못했습니다.</div>
        : !item ? <div className="carpool-empty">모집글을 불러오는 중입니다.</div>
          : <>
            <article className="carpool-detail-card">
              <Link href="/carpool" className="carpool-back">← 카풀·동행 목록</Link>
              <div className="carpool-detail-head">
                <div className="carpool-detail-badges">
                  <span>{item.tripType === "ROUND_TRIP" ? "왕복" : "편도"}</span>
                  <span className={item.equipmentLoadAvailable ? "equipment-available" : "equipment-unavailable"}>
                    <span className="material-symbols-outlined">luggage</span>
                    장비 적재 {item.equipmentLoadAvailable ? "가능" : "불가능"}
                  </span>
                </div>
                <h1>{item.title}</h1>
                <p><strong>{item.writerName}</strong><span>·</span>{new Date(item.departureAt).toLocaleString("ko-KR")}</p>
                {item.canManage && <div className="carpool-detail-actions"><Link href={`/carpool/${publicId}/edit`}>수정</Link><button type="button" disabled={deleting} onClick={() => void deleteCarpool()}>{deleting ? "삭제 중" : "삭제"}</button></div>}
              </div>
              <section className="carpool-detail-route" aria-label="이동 경로">
                <div className="carpool-route-point start"><i /><div><small>출발 장소</small><strong>{item.departureRegion}</strong><span>{item.meetingPlace}</span></div></div>
                <div className="carpool-route-line"><span className="material-symbols-outlined">arrow_forward</span></div>
                <div className="carpool-route-point end"><i /><div><small>도착 리조트</small><strong>{item.destinationResortName}</strong><span>{new Date(item.departureAt).toLocaleDateString("ko-KR")} 출발</span></div></div>
              </section>
              <section className="carpool-cost-panel">
                <div className="carpool-cost-heading">
                  <div><span>예상 1인 부담액</span><strong>{item.estimatedCostPerPerson.toLocaleString("ko-KR")}<small>원</small></strong></div>
                  <div className="carpool-cost-totals">
                    <div><span>모집 인원</span><b>{item.passengerCapacity}명</b></div>
                    <div><span>예상 총액</span><b>{item.estimatedTotalCost.toLocaleString("ko-KR")}원</b></div>
                  </div>
                </div>
                <div className="carpool-cost-breakdown">
                  <div><span className="material-symbols-outlined">route</span><p><small>{item.routeSource === "MANUAL" ? "직접 입력 거리" : "이동 거리"}</small><b>{item.routeDistanceKm.toLocaleString("ko-KR")}km</b></p></div>
                  <div><span className="material-symbols-outlined">toll</span><p><small>통행료</small><b>{item.routeTollFee.toLocaleString("ko-KR")}원</b></p></div>
                  <div><span className="material-symbols-outlined">local_gas_station</span><p><small>{item.fuelPriceSource === "USER_INPUT" ? "사용자 입력 유가" : "기준 유가"}</small><b>{item.fuelPrice.toLocaleString("ko-KR")}원/L</b></p></div>
                  <div><span className="material-symbols-outlined">payments</span><p><small>예상 연료비</small><b>{item.estimatedFuelCost.toLocaleString("ko-KR")}원</b></p></div>
                </div>
                <p className="carpool-fuel-time">
                  {item.routeSource === "MANUAL" && "경로·통행료 사용자 직접 입력 · "}
                  {item.fuelPriceSource === "CACHE"
                    ? "오피넷 유효 캐시"
                    : item.fuelPriceSource === "USER_INPUT"
                      ? "유가 사용자 직접 입력"
                      : "오피넷 유가"}{" "}
                  {new Date(item.fuelPriceObservedAt).toLocaleString("ko-KR")} 기준
                </p>
              </section>
              <section className="carpool-content-panel"><h2><span className="material-symbols-outlined">description</span>모집 내용</h2><p>{item.content}</p></section>
              {item.contactInfo && <div className="carpool-contact-result"><span className="material-symbols-outlined">chat</span><div><small>연락 방법</small><strong>{item.contactInfo}</strong></div></div>}
            </article>
            <PostComments postPublicId={publicId} />
          </>}
    </main>
    <Footer />
  </>;
}
