"use client";

import React, { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import { API_BASE_URL, API_ENDPOINTS } from "../lib/api";
import { RESORT_MAP, RESORT_OPTIONS } from "../lib/resortTags";

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

const LOCAL_STORAGE_TAG_KEY = "snowthing_chat_resort";
const MAX_DOM_MESSAGES = 100;
const RECONNECT_BASE_DELAY_MILLIS = 1000;
const RECONNECT_MAX_DELAY_MILLIS = 30000;
const RECONNECT_JITTER_MILLIS = 1000;

function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const messagesById = new Map<string, ChatMessage>();
  for (const message of [...current, ...incoming]) {
    messagesById.set(message.messageId, message);
  }
  return [...messagesById.values()]
    .sort((left, right) => new Date(left.sentAt).getTime() - new Date(right.sentAt).getTime())
    .slice(-MAX_DOM_MESSAGES);
}

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
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputContent, setInputContent] = useState("");
  const [selectedResort, setSelectedResort] = useState<string>(() => {
    if (typeof window === "undefined") return "";

    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_TAG_KEY);
      return saved && RESORT_MAP[saved] ? saved : "";
    } catch {
      return "";
    }
  });
  const [isConnected, setIsConnected] = useState(false);
  const [toastError, setToastError] = useState<string | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);

  const stompClientRef = useRef<Client | null>(null);
  const reconnectAttemptRef = useRef(0);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const isAtBottomRef = useRef<boolean>(true);

  // 1. 리조트 태그 변경 핸들러
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

  // 2. 스크롤 위치 감지 (Scroll Anchoring)
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
      reconnectDelay: RECONNECT_BASE_DELAY_MILLIS,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        reconnectAttemptRef.current = 0;
        client.reconnectDelay = RECONNECT_BASE_DELAY_MILLIS;
        setIsConnected(true);

        // 메인 광장 브로드캐스트 채널 구독
        client.subscribe("/sub/chat/main", (frame) => {
          try {
            const newMsg: ChatMessage = JSON.parse(frame.body);
            setMessages((previous) => mergeMessages(previous, [newMsg]));

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

        // 구독을 먼저 마친 뒤 최근 메시지를 합쳐 조회-구독 사이 유실과 중복을 줄인다.
        fetch(API_ENDPOINTS.chat.recent)
          .then((response) => (response.ok ? response.json() : []))
          .then((recentMessages: ChatMessage[]) => {
            if (Array.isArray(recentMessages)) {
              setMessages((previous) => mergeMessages(previous, recentMessages));
              setTimeout(() => {
                if (isAtBottomRef.current) {
                  scrollToBottom("auto");
                }
              }, 50);
            }
          })
          .catch(() => {});
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: () => {
        setIsConnected(false);
      },
      onWebSocketClose: () => {
        setIsConnected(false);
        reconnectAttemptRef.current += 1;
        const exponentialDelay =
          RECONNECT_BASE_DELAY_MILLIS * 2 ** Math.min(reconnectAttemptRef.current, 5);
        const jitter = Math.floor(Math.random() * RECONNECT_JITTER_MILLIS);
        client.reconnectDelay = Math.min(RECONNECT_MAX_DELAY_MILLIS, exponentialDelay + jitter);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
      stompClientRef.current = null;
    };
  }, [currentMember?.publicId]);

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
      router.push("/login");
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
          className="live-chat-feed"
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
              return (
                <div key={msg.messageId} className={`live-chat-message ${isMine ? "mine" : "other"}`}>
                  <div className="live-chat-message-meta">
                    {!isMine && <strong>{msg.sender.nickname}</strong>}
                    <span className="live-chat-message-tag">
                      <i className={`rounded-full ${resort ? resort.markerClass : "bg-slate-400"}`} />
                      {resort ? resort.koreanName : "일반"}
                    </span>
                    <time>{formatRelativeTime(msg.sentAt)}</time>
                  </div>
                  <div className="live-chat-message-bubble">{msg.content}</div>
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
      <form onSubmit={handleSubmit} className="live-chat-compose">
        {/* 리조트 태그 드롭다운 (비로그인 시 일반 고정 잠금) */}
        <select
          value={currentMember ? selectedResort : ""}
          onChange={handleResortChange}
          disabled={!currentMember}
          className="live-chat-compose-select"
        >
          {RESORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        {/* 텍스트 입력창 (100자 제한, 비로그인 시 클릭/포커스 시 로그인 페이지 이동 유도) */}
        <div className="relative flex-1">
          <input
            type="text"
            maxLength={100}
            value={inputContent}
            onChange={(e) => setInputContent(e.target.value)}
            onClick={() => {
              if (!currentMember) {
                router.push("/login");
              }
            }}
            onFocus={() => {
              if (!currentMember) {
                router.push("/login");
              }
            }}
            placeholder={
              currentMember
                ? "메세지를 입력하세요"
                : "로그인하고 라이브톡에 참여해보세요"
            }
            className="live-chat-compose-input"
          />
        </div>

        {/* 전송 버튼 */}
        <button
          type="submit"
          disabled={Boolean(currentMember && !inputContent.trim())}
          className="live-chat-compose-submit"
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
