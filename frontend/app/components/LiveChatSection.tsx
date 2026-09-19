"use client";

import React, { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { API_BASE_URL } from "../lib/api";

export interface MemberProfile {
  publicId: string;
  email: string;
  nickname: string;
  role?: string;
}

interface ChatSender {
  publicId: string;
  nickname: string;
}

interface ChatMessage {
  messageId: string;
  sender: ChatSender;
  resortTag: string | null;
  content: string;
  sentAt: string;
}

interface ChatError {
  code: string;
  message: string;
  timestamp: string;
}

interface ResortMeta {
  code: string;
  koreanName: string;
  shortName: string;
  badgeClass: string;
  avatarClass: string;
}

const RESORT_MAP: Record<string, ResortMeta> = {
  PHOENIX: {
    code: "PHOENIX",
    koreanName: "휘닉스",
    shortName: "휘팍",
    badgeClass: "bg-sky-50 text-sky-700 border-sky-200",
    avatarClass: "bg-sky-100 text-sky-700 border-sky-300",
  },
  VIVALDI: {
    code: "VIVALDI",
    koreanName: "비발디",
    shortName: "비발",
    badgeClass: "bg-amber-50 text-amber-700 border-amber-200",
    avatarClass: "bg-amber-100 text-amber-800 border-amber-300",
  },
  HIGH1: {
    code: "HIGH1",
    koreanName: "하이원",
    shortName: "하이",
    badgeClass: "bg-teal-50 text-teal-700 border-teal-200",
    avatarClass: "bg-teal-100 text-teal-800 border-teal-300",
  },
  YONGPYONG: {
    code: "YONGPYONG",
    koreanName: "용평",
    shortName: "용평",
    badgeClass: "bg-rose-50 text-rose-700 border-rose-200",
    avatarClass: "bg-rose-100 text-rose-700 border-rose-300",
  },
  WELLI_HILLI: {
    code: "WELLI_HILLI",
    koreanName: "웰팍",
    shortName: "웰팍",
    badgeClass: "bg-emerald-50 text-emerald-700 border-emerald-200",
    avatarClass: "bg-emerald-100 text-emerald-700 border-emerald-300",
  },
  ETC: {
    code: "ETC",
    koreanName: "기타",
    shortName: "기타",
    badgeClass: "bg-slate-100 text-slate-700 border-slate-200",
    avatarClass: "bg-slate-200 text-slate-700 border-slate-300",
  },
};

const RESORT_OPTIONS = [
  { value: "", label: "선택 안 함 (잡담)" },
  { value: "PHOENIX", label: "휘닉스" },
  { value: "VIVALDI", label: "비발디" },
  { value: "HIGH1", label: "하이원" },
  { value: "YONGPYONG", label: "용평" },
  { value: "WELLI_HILLI", label: "웰팍" },
  { value: "ETC", label: "기타" },
];

const LOCAL_STORAGE_TAG_KEY = "snowthing_chat_resort";
const MAX_DOM_MESSAGES = 100;

function formatRelativeTime(isoString: string): string {
  try {
    const sent = new Date(isoString);
    const now = new Date();
    const diffSec = Math.floor((now.getTime() - sent.getTime()) / 1000);

    if (diffSec < 10) return "방금";
    if (diffSec < 60) return `${diffSec}초 전`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}분 전`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;

    return sent.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
  } catch {
    return "방금";
  }
}

interface LiveChatSectionProps {
  currentMember: MemberProfile | null;
}

export default function LiveChatSection({ currentMember }: LiveChatSectionProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputContent, setInputContent] = useState("");
  const [selectedResort, setSelectedResort] = useState<string>("");
  const [isConnected, setIsConnected] = useState(false);
  const [toastError, setToastError] = useState<string | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);

  const stompClientRef = useRef<Client | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const isAtBottomRef = useRef<boolean>(true);

  // 1. 브라우저 localStorage에서 리조트 태그 복원
  useEffect(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_TAG_KEY);
      if (saved && RESORT_MAP[saved]) {
        setSelectedResort(saved);
      }
    } catch {
      // localStorage 접근 불가 환경 대비
    }
  }, []);

  // 2. 리조트 태그 변경 핸들러
  const handleResortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    setSelectedResort(value);
    try {
      if (value) {
        localStorage.setItem(LOCAL_STORAGE_TAG_KEY, value);
      } else {
        localStorage.removeItem(LOCAL_STORAGE_TAG_KEY);
      }
    } catch {
      // ignore
    }
  };

  // 3. 스크롤 위치 감지 (Scroll Anchoring)
  const handleScroll = () => {
    const el = scrollContainerRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const atBottom = distanceToBottom < 35;
    isAtBottomRef.current = atBottom;
    setShowScrollBottom(!atBottom);
  };

  const scrollToBottom = (behavior: ScrollBehavior = "smooth") => {
    const el = scrollContainerRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior });
    isAtBottomRef.current = true;
    setShowScrollBottom(false);
  };

  // 4. 웹소켓 STOMP 클라이언트 연결 생명주기
  useEffect(() => {
    const wsUrl = API_BASE_URL.replace(/^http/, "ws") + "/ws-chat";

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setIsConnected(true);

        // 메인 광장 브로드캐스트 채널 구독
        client.subscribe("/sub/chat/main", (frame) => {
          try {
            const newMsg: ChatMessage = JSON.parse(frame.body);
            setMessages((prev) => {
              const updated = [...prev.slice(-(MAX_DOM_MESSAGES - 1)), newMsg];
              return updated;
            });

            // 스크롤이 바닥 근처에 있을 때만 자동 스크롤
            setTimeout(() => {
              if (isAtBottomRef.current) {
                scrollToBottom("smooth");
              } else {
                setShowScrollBottom(true);
              }
            }, 50);
          } catch {
            // ignore parse error
          }
        });

        // 개인 에러 큐 구독 (도배/외부링크/금칙어 차단)
        client.subscribe("/user/queue/errors", (frame) => {
          try {
            const errorPayload: ChatError = JSON.parse(frame.body);
            setToastError(errorPayload.message);
          } catch {
            setToastError("메시지 전송이 거부되었습니다.");
          }
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: () => {
        setIsConnected(false);
      },
      onWebSocketClose: () => {
        setIsConnected(false);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
      stompClientRef.current = null;
    };
  }, []);

  // 5. 토스트 에러 자동 닫기 (4초)
  useEffect(() => {
    if (!toastError) return;
    const timer = setTimeout(() => setToastError(null), 4000);
    return () => clearTimeout(timer);
  }, [toastError]);

  // 6. 메시지 전송
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentMember) {
      setToastError("로그인 후 라이브톡에 참여할 수 있습니다.");
      return;
    }
    const trimmed = inputContent.trim();
    if (!trimmed) return;

    if (!stompClientRef.current || !isConnected) {
      setToastError("서버와 실시간 스트림 연결 중입니다. 잠시만 기다려주세요.");
      return;
    }

    const payload = {
      resortTag: selectedResort || null,
      content: trimmed,
    };

    stompClientRef.current.publish({
      destination: "/pub/chat/messages",
      body: JSON.stringify(payload),
    });

    setInputContent("");
  };

  return (
    <section aria-label="실시간 라이브톡" className="bg-white border border-slate-200 rounded-lg p-3 shadow-xs mb-6 relative">
      {/* 상단 헤더 바 */}
      <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <span className="flex h-2.5 w-2.5 relative">
            <span
              className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${
                isConnected ? "bg-emerald-400" : "bg-amber-400"
              }`}
            />
            <span
              className={`relative inline-flex rounded-full h-2.5 w-2.5 ${
                isConnected ? "bg-emerald-500" : "bg-amber-500"
              }`}
            />
          </span>
          <h2 className="font-extrabold text-slate-900 text-sm tracking-tight flex items-center gap-1.5">
            <span className="text-amber-500">⚡</span>
            <span>라이브톡</span>
          </h2>
        </div>
        <div className="flex items-center gap-2 text-slate-400 text-xs">
          <span className="inline-flex items-center gap-1 text-[11px] text-slate-500 font-medium">
            <span
              className={`w-1.5 h-1.5 rounded-full ${isConnected ? "bg-emerald-500" : "bg-amber-500"}`}
            />
            {isConnected ? "실시간 스트림 연결됨" : "스트림 재연결 중..."}
          </span>
        </div>
      </div>

      {/* 실시간 말풍선 피드 영역 */}
      <div className="relative my-2.5">
        <div
          ref={scrollContainerRef}
          onScroll={handleScroll}
          className="bg-slate-50 border border-slate-100 rounded-lg p-3 h-64 overflow-y-auto space-y-3.5 text-xs select-text"
        >
          {messages.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-slate-400 text-xs gap-1">
              <span className="text-sm">🏂</span>
              <p>실시간 슬로프 현황이나 오늘의 라이딩 잡담을 나눠보세요!</p>
            </div>
          ) : (
            messages.map((msg) => {
              const isMine = Boolean(currentMember && msg.sender.publicId === currentMember.publicId);
              const resort = msg.resortTag ? RESORT_MAP[msg.resortTag] : null;

              if (isMine) {
                // 내 메시지: 우측 정렬, 하늘색 말풍선
                return (
                  <div key={msg.messageId} className="flex justify-end items-end gap-1.5">
                    <span className="text-[10px] text-slate-400 font-mono shrink-0 mb-0.5">
                      {formatRelativeTime(msg.sentAt)}
                    </span>
                    <div className="bg-sky-50 text-sky-950 border border-sky-100 rounded-2xl rounded-tr-xs px-3.5 py-2 text-xs max-w-[80%] break-words shadow-2xs">
                      {msg.content}
                    </div>
                  </div>
                );
              }

              // 타인 메시지: 좌측 정렬, 원형 아바타 + 닉네임 + 리조트 뱃지 + 흰색 말풍선
              return (
                <div key={msg.messageId} className="flex items-start gap-2 max-w-[88%]">
                  {/* 원형 아바타 (리조트 약칭 또는 기본 유저) */}
                  {resort ? (
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center text-[10px] font-extrabold border shrink-0 mt-0.5 shadow-2xs ${resort.avatarClass}`}
                    >
                      {resort.shortName}
                    </div>
                  ) : (
                    <div className="w-7 h-7 rounded-full bg-slate-200 border border-slate-300 text-slate-600 flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">
                      {msg.sender.nickname.slice(0, 1)}
                    </div>
                  )}

                  {/* 닉네임 및 말풍선 본문 */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5 mb-1">
                      <span className="font-bold text-slate-800 text-[11px] truncate">
                        {msg.sender.nickname}
                      </span>
                      {resort && (
                        <span
                          className={`px-1.5 py-0.2 rounded-sm text-[10px] font-bold border shrink-0 ${resort.badgeClass}`}
                        >
                          {resort.koreanName}
                        </span>
                      )}
                    </div>
                    <div className="flex items-end gap-1.5">
                      <div className="bg-white text-slate-800 border border-slate-200/90 rounded-2xl rounded-tl-xs px-3.5 py-2 text-xs break-words shadow-2xs">
                        {msg.content}
                      </div>
                      <span className="text-[10px] text-slate-400 font-mono shrink-0 mb-0.5">
                        {formatRelativeTime(msg.sentAt)}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* 스크롤 앵커링: 새 메시지 알림 버튼 */}
        {showScrollBottom && (
          <button
            type="button"
            onClick={() => scrollToBottom("smooth")}
            className="absolute bottom-3 left-1/2 -translate-x-1/2 bg-[#0f2942] hover:bg-sky-700 text-white text-[11px] font-bold px-3 py-1.5 rounded-full shadow-lg flex items-center gap-1 transition-all animate-bounce"
          >
            <span>새 메시지 수신</span>
            <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
            </svg>
          </button>
        )}

        {/* 도배/링크 차단 개인 에러 토스트 */}
        {toastError && (
          <div className="absolute top-2 left-1/2 -translate-x-1/2 bg-rose-600 text-white text-xs font-semibold px-4 py-2 rounded-md shadow-lg flex items-center gap-2 z-20 animate-fade-in">
            <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span>{toastError}</span>
          </div>
        )}
      </div>

      {/* 하단 입력 폼 바 */}
      <form onSubmit={handleSubmit} className="pt-2 border-t border-slate-100 flex items-center gap-2">
        {/* 리조트 태그 드롭다운 */}
        <select
          value={selectedResort}
          onChange={handleResortChange}
          disabled={!currentMember}
          className="text-xs bg-slate-50 border border-slate-200 rounded-md px-2 py-2 outline-none text-slate-700 font-semibold focus:border-sky-500 transition-colors shrink-0 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        >
          {RESORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        {/* 텍스트 입력창 (100자 제한) */}
        <div className="relative flex-1">
          <input
            type="text"
            maxLength={100}
            value={inputContent}
            onChange={(e) => setInputContent(e.target.value)}
            disabled={!currentMember}
            placeholder={
              currentMember
                ? "실시간 슬로프 상황이나 잡담을 나눠보세요... (최대 100자)"
                : "로그인 후 실시간 라이브톡에 참여할 수 있습니다."
            }
            className="w-full text-xs bg-slate-50 border border-slate-200 rounded-md px-3 py-2 outline-none focus:bg-white focus:border-sky-600 transition-all placeholder:text-slate-400 disabled:opacity-60 disabled:cursor-not-allowed"
          />
        </div>

        {/* 전송 버튼 */}
        <button
          type="submit"
          disabled={!currentMember || !inputContent.trim()}
          className="px-4 py-2 bg-[#0f2942] hover:bg-sky-700 text-white font-bold rounded-md text-xs shrink-0 transition-all flex items-center gap-1.5 shadow-xs disabled:opacity-50 disabled:cursor-not-allowed active:scale-95"
        >
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
          <span>전송</span>
        </button>
      </form>
    </section>
  );
}
