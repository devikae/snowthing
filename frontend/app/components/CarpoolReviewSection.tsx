"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { carpoolReviewTagLabel, responseMessage, type CarpoolReviewRating, type CarpoolReviewTag } from "../carpool/review";
import { formatCarpoolDateTime } from "../carpool/format";
import "../carpool/review.css";
import { API_ENDPOINTS } from "../lib/api";
import { errorText } from "../lib/errorText";

interface MyCarpoolReviews {
  rideCount: number;
  reviewCount: number;
  goodCount: number;
  topTags: { tag: CarpoolReviewTag; count: number }[];
  received: {
    rating: CarpoolReviewRating;
    tags: CarpoolReviewTag[];
    comment: string | null;
    carpoolPublicId: string;
    carpoolTitle: string;
    createdAt: string;
  }[];
}

/** 내 정보의 카풀 후기. 받은 후기 전체 수와 비율, 받은 후기 글은 본인에게만 보인다. */
export function CarpoolReviewSection() {
  const [data, setData] = useState<MyCarpoolReviews | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    void fetch(API_ENDPOINTS.carpool.myReviews, { credentials: "include", cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await responseMessage(response, "카풀 후기를 불러오지 못했습니다."));
        return (await response.json()) as MyCarpoolReviews;
      })
      .then((value) => {
        if (!cancelled) setData(value);
      })
      .catch((loadError) => {
        if (!cancelled) setError(errorText(loadError, "카풀 후기를 불러오지 못했습니다."));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const goodRate = data && data.reviewCount > 0 ? Math.round((data.goodCount / data.reviewCount) * 100) : null;

  return (
    <section className="profile-section">
      <div>
        <h2>카풀 후기</h2>
        <p>다른 회원에게는 카풀 횟수와 좋았어요 개수만 보입니다.</p>
      </div>
      <div className="carpool-my-reviews">
        {error && <p role="alert">{error}</p>}
        {!data ? (
          !error && <p className="carpool-my-reviews-meta">카풀 후기를 불러오는 중입니다.</p>
        ) : (
          <>
            <dl className="carpool-my-reviews-stats">
              <div><dt>카풀 참여</dt><dd>{data.rideCount}회</dd></div>
              <div><dt>받은 후기</dt><dd>{data.reviewCount}개</dd></div>
              <div><dt>좋았어요</dt><dd>{goodRate === null ? "없음" : `${data.goodCount}개 (${goodRate}%)`}</dd></div>
            </dl>
            {data.topTags.length > 0 && (
              <p className="carpool-my-reviews-meta">
                많이 받은 좋았던 점: {data.topTags.map((item) => `${carpoolReviewTagLabel(item.tag)} ${item.count}`).join(", ")}
              </p>
            )}
            {data.received.length === 0 ? (
              <p className="carpool-my-reviews-meta">아직 받은 후기가 없습니다.</p>
            ) : (
              <ul className="carpool-my-reviews-list">
                {data.received.map((review, index) => (
                  <li key={`${review.carpoolPublicId}-${review.createdAt}-${index}`}>
                    <strong>{review.rating === "GOOD" ? "좋았어요" : "아쉬웠어요"}</strong>
                    {review.tags.length > 0 && <span>{review.tags.map(carpoolReviewTagLabel).join(", ")}</span>}
                    {review.comment && <p>{review.comment}</p>}
                    <small>
                      <Link href={`/carpool/${review.carpoolPublicId}`}>{review.carpoolTitle}</Link>, {formatCarpoolDateTime(review.createdAt)}
                    </small>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </section>
  );
}
