"use client";

import { useCallback, useEffect, useState } from "react";
import { CommentProfileAvatar } from "../../components/CommentProfileAvatar";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { errorText } from "../../lib/errorText";
import { fetchViewer, type Viewer } from "../../lib/viewer";
import { CARPOOL_REVIEW_TAGS, responseMessage, type CarpoolReviewRating, type CarpoolReviewTag } from "../review";
import "../review.css";

interface Participant {
  publicId: string | null;
  nickname: string;
  profileImageUrl: string | null;
  role: "DRIVER" | "RIDER";
  status: "REQUESTED" | "CONFIRMED" | "REJECTED";
  reviewedByMe: boolean;
}

interface Participation {
  myStatus: "DRIVER" | "REQUESTED" | "CONFIRMED" | "REJECTED" | null;
  departed: boolean;
  reviewOpen: boolean;
  confirmedCount: number;
  passengerCapacity: number;
  members: Participant[];
}

const COMMENT_MAX = 100;

/** 카풀 상세의 "함께 탄 사람". 출발 뒤에만 보인다. 설계: docs/conception/027_carpool_review/01_design.md */
export function CarpoolParticipation({ publicId }: { publicId: string }) {
  const [data, setData] = useState<Participation | null>(null);
  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reviewTarget, setReviewTarget] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [response, me] = await Promise.all([
        fetch(API_ENDPOINTS.carpool.participation(publicId), { credentials: "include", cache: "no-store" }),
        fetchViewer(),
      ]);
      if (!response.ok) throw new Error(await responseMessage(response, "함께 탄 사람을 불러오지 못했습니다."));
      setData(await response.json());
      setViewer(me);
    } catch (loadError) {
      setError(errorText(loadError, "함께 탄 사람을 불러오지 못했습니다."));
    }
  }, [publicId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const send = async (url: string, fallback: string, body?: unknown) => {
    if (busy) return false;
    setBusy(true);
    setError("");
    try {
      const response = await csrfFetch(url, {
        method: "POST",
        ...(body ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
      });
      if (!response.ok) throw new Error(await responseMessage(response, fallback));
      await load();
      return true;
    } catch (sendError) {
      setError(errorText(sendError, fallback));
      return false;
    } finally {
      setBusy(false);
    }
  };

  if (!data || !data.departed) {
    return error ? <p className="carpool-ride-error" role="alert">{error}</p> : null;
  }
  const participant = data.myStatus === "DRIVER" || data.myStatus === "CONFIRMED";
  // 로그인하지 않았거나, 탑승 확인 기간이 지났는데 관계도 없는 사람에게는 보여 줄 것이 없다.
  if (!viewer || (!data.myStatus && !data.reviewOpen)) return null;

  return (
    <section className="carpool-ride" aria-labelledby="carpool-ride-title">
      <h2 id="carpool-ride-title">함께 탄 사람</h2>
      {error && <p className="carpool-ride-error" role="alert">{error}</p>}

      {!data.myStatus && (
        <div className="carpool-ride-notice">
          <p>이 카풀을 함께 탔다면 탑승 확인을 요청해 주세요. 모집한 회원이 확인하면 서로 후기를 남길 수 있어요.</p>
          <button
            type="button"
            className="carpool-ride-button primary"
            disabled={busy}
            onClick={() => void send(API_ENDPOINTS.carpool.requestRide(publicId), "탑승 확인을 요청하지 못했습니다.")}
          >
            이 카풀을 탔어요
          </button>
        </div>
      )}
      {data.myStatus === "REQUESTED" && <p className="carpool-ride-notice">모집한 회원의 확인을 기다리고 있어요.</p>}
      {data.myStatus === "REJECTED" && <p className="carpool-ride-notice">모집한 회원이 탑승을 확인하지 않았어요.</p>}

      {participant && (
        <>
          <p className="carpool-ride-meta">
            {data.reviewOpen
              ? "후기는 출발 후 30일까지 남길 수 있어요. 글과 아쉬웠어요는 받은 사람에게만 보이고, 누가 썼는지는 표시하지 않아요."
              : "후기를 남길 수 있는 기간(출발 후 30일)이 지났어요."}
          </p>
          <ul className="carpool-ride-list">
            {data.members.map((member) => {
              const key = member.publicId ?? `${member.role}-${member.nickname}`;
              const isMe = viewer.publicId === member.publicId;
              return (
                <li key={key}>
                  <div className="carpool-ride-member">
                    <CommentProfileAvatar nickname={member.nickname} profileImageUrl={member.profileImageUrl} size={32} />
                    <div>
                      <strong>{member.nickname}{isMe ? " (나)" : ""}</strong>
                      <small>{member.role === "DRIVER" ? "모집한 회원" : member.status === "REQUESTED" ? "탑승 확인 요청" : "탑승"}</small>
                    </div>
                    <div className="carpool-ride-actions">
                      {member.status === "REQUESTED" && data.myStatus === "DRIVER" && member.publicId && (
                        <>
                          <button
                            type="button"
                            className="carpool-ride-button primary"
                            disabled={busy}
                            onClick={() => void send(API_ENDPOINTS.carpool.decideRide(publicId, member.publicId!, "confirm"), "탑승을 확정하지 못했습니다.")}
                          >
                            확정
                          </button>
                          <button
                            type="button"
                            className="carpool-ride-button"
                            disabled={busy}
                            onClick={() => {
                              if (window.confirm(`${member.nickname}님의 탑승 확인 요청을 거절할까요? 거절하면 다시 요청할 수 없어요.`)) {
                                void send(API_ENDPOINTS.carpool.decideRide(publicId, member.publicId!, "reject"), "요청을 거절하지 못했습니다.");
                              }
                            }}
                          >
                            거절
                          </button>
                        </>
                      )}
                      {member.status === "CONFIRMED" && !isMe && member.publicId && (
                        member.reviewedByMe ? (
                          <span className="carpool-ride-done">후기를 남겼어요</span>
                        ) : data.reviewOpen && reviewTarget !== member.publicId ? (
                          <button type="button" className="carpool-ride-button" onClick={() => setReviewTarget(member.publicId)}>
                            후기 남기기
                          </button>
                        ) : null
                      )}
                    </div>
                  </div>
                  {reviewTarget === member.publicId && member.publicId && (
                    <ReviewForm
                      target={member}
                      busy={busy}
                      onCancel={() => setReviewTarget(null)}
                      onSubmit={async (body) => {
                        const ok = await send(API_ENDPOINTS.carpool.reviews(publicId), "후기를 남기지 못했습니다.", {
                          revieweePublicId: member.publicId,
                          ...body,
                        });
                        if (ok) setReviewTarget(null);
                      }}
                    />
                  )}
                </li>
              );
            })}
          </ul>
          {data.myStatus === "DRIVER" && data.members.length <= 1 && (
            <p className="carpool-ride-meta">함께 탄 회원이 탑승 확인을 요청하면 여기에서 확정할 수 있어요.</p>
          )}
        </>
      )}
    </section>
  );
}

function ReviewForm({
  target,
  busy,
  onCancel,
  onSubmit,
}: {
  target: Participant;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (body: { rating: CarpoolReviewRating; tags: CarpoolReviewTag[]; comment: string | null }) => Promise<void>;
}) {
  const [rating, setRating] = useState<CarpoolReviewRating | null>(null);
  const [tags, setTags] = useState<CarpoolReviewTag[]>([]);
  const [comment, setComment] = useState("");
  const tagOptions = CARPOOL_REVIEW_TAGS.filter((item) => target.role === "DRIVER" || !item.driverOnly);

  const toggleTag = (tag: CarpoolReviewTag) =>
    setTags((current) => (current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag]));

  return (
    <form
      className="carpool-review-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (!rating) return;
        void onSubmit({ rating, tags: rating === "GOOD" ? tags : [], comment: comment.trim() || null });
      }}
    >
      <fieldset disabled={busy}>
        <legend>{target.nickname}님과의 카풀은 어땠나요?</legend>
        <div className="carpool-choice">
          <button type="button" aria-pressed={rating === "GOOD"} onClick={() => setRating("GOOD")}>좋았어요</button>
          <button type="button" aria-pressed={rating === "BAD"} onClick={() => setRating("BAD")}>아쉬웠어요</button>
        </div>
        {rating === "GOOD" && (
          <div className="carpool-review-tags" role="group" aria-label="좋았던 점">
            {tagOptions.map((item) => (
              <button key={item.tag} type="button" aria-pressed={tags.includes(item.tag)} onClick={() => toggleTag(item.tag)}>
                {item.label}
              </button>
            ))}
          </div>
        )}
        <label>
          <span>한마디 (선택, {comment.length}/{COMMENT_MAX})</span>
          <textarea
            value={comment}
            maxLength={COMMENT_MAX}
            rows={2}
            placeholder={rating === "BAD" ? "아쉬웠던 점을 정중하게 전해 주세요." : "고마운 마음을 전해 보세요."}
            onChange={(event) => setComment(event.target.value)}
          />
        </label>
        <div className="carpool-review-form-actions">
          <button type="button" className="carpool-ride-button" onClick={onCancel}>취소</button>
          <button type="submit" className="carpool-ride-button primary" disabled={!rating}>후기 남기기</button>
        </div>
      </fieldset>
    </form>
  );
}
