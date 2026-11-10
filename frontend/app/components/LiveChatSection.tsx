"use client";

import React, { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Client } from "@stomp/stompjs";
import { API_BASE_URL, API_ENDPOINTS } from "../lib/api";
import { RESORT_MAP, RESORT_OPTIONS } from "../lib/resortTags";
import { localChatCommand, type LocalChatCommand } from "../lib/chatCommands";
import { csrfFetch } from "../lib/csrfFetch";

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
const GUEST_ECHO_WINDOW_MILLIS = 15000;
const MAX_DOM_MESSAGES = 100;
const SEND_INTERVAL_MILLIS = 1000;
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
  const [localHelp, setLocalHelp] = useState<LocalChatCommand | null>(null);
  // 관리자가 비회원 발신을 켜면 로그인 없이도 보낼 수 있다. 서버가 같은 설정으로 다시 확인한다.
  const [anonymousChatEnabled, setAnonymousChatEnabled] = useState(false);
  const [myGuestMessageIds, setMyGuestMessageIds] = useState<Set<string>>(() => new Set());
  const pendingGuestSendsRef = useRef<{ content: string; time: number }[]>([]);
  const canSend = Boolean(currentMember) || anonymousChatEnabled;
  const isAdmin = currentMember?.role === "ROLE_ADMIN";

  const stompClientRef = useRef<Client | null>(null);
  const reconnectAttemptRef = useRef(0);
  const lastSentAtRef = useRef(0);
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
    let cancelled = false;
    fetch(API_ENDPOINTS.public.configs)
      .then((response) => (response.ok ? response.json() : null))
      .then((config: { anonymousChatEnabled?: boolean } | null) => {
        if (!cancelled && config) setAnonymousChatEnabled(config.anonymousChatEnabled === true);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

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
            const payload = JSON.parse(frame.body);
            if (payload.type === "PURGE_MESSAGES") {
              const purged = new Set<string>(Array.isArray(payload.messageIds) ? payload.messageIds : []);
              setMessages((previous) => previous.filter((message) => !purged.has(message.messageId)));
              return;
            }
            if (payload.type === "CONFIG_UPDATED") {
              if (payload.key === "CHAT_ANONYMOUS_ENABLED") setAnonymousChatEnabled(payload.value === true);
              return;
            }
            const newMsg: ChatMessage = payload;
            // 비회원 메시지는 매번 새 ID로 오므로 방금 보낸 내용과 맞춰 내 메시지로 표시한다.
            const pendingIndex = pendingGuestSendsRef.current.findIndex(
              (pending) => pending.content === newMsg.content && Date.now() - pending.time < GUEST_ECHO_WINDOW_MILLIS,
            );
            if (pendingIndex !== -1 && newMsg.sender.publicId.startsWith("anon-")) {
              pendingGuestSendsRef.current.splice(pendingIndex, 1);
              setMyGuestMessageIds((previous) => new Set(previous).add(newMsg.messageId));
            }
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
    const command = localChatCommand(inputContent);
    if (command) {
      setLocalHelp(command);
      setInputContent("");
      return;
    }
    if (!canSend) {
      router.push("/login");
      return;
    }
    const trimmed = inputContent.trim();
    if (!trimmed) return;

    if (!stompClientRef.current || !isConnected) {
      setToastError("서버와 실시간 스트림 연결 중입니다. 잠시만 기다려주세요.");
      return;
    }

    // 서버는 1초에 1건만 받는다. 거절될 전송으로 입력이 지워지지 않게 여기서 먼저 막는다.
    const now = Date.now();
    if (now - lastSentAtRef.current < SEND_INTERVAL_MILLIS) {
      setToastError("메시지 전송 속도가 너무 빠릅니다. 잠시 후 다시 시도해 주세요.");
      return;
    }
    lastSentAtRef.current = now;
    if (!currentMember) {
      pendingGuestSendsRef.current.push({ content: trimmed, time: now });
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

  const banMessageSender = async (message: ChatMessage) => {
    if (!window.confirm(`${message.sender.nickname}의 IP를 7일 동안 차단하고 최근 메시지를 지울까요?`)) return;
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.banMessageIp(message.messageId), { method: "POST" });
      if (!response.ok) throw new Error();
      setMessages((previous) => previous.filter((item) => item.messageId !== message.messageId));
    } catch {
      setToastError("IP를 차단하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <section aria-label="실시간 라이브톡" className="live-chat-card relative">
      {/* 상단 헤더 바 */}
      <div className="live-chat-head">
        <div className="section-head-title">
          <small className="section-eyebrow">실시간 채팅</small>
          <h2>라이브톡</h2>
        </div>
      </div>

      {localHelp&&<aside id="live-chat-local-help" className="live-chat-local-help" aria-label="라이브톡 도구 안내">
        <button type="button" className="live-chat-local-help-close" aria-label="안내 닫기" onClick={()=>setLocalHelp(null)}><span className="material-symbols-outlined" aria-hidden="true">close</span></button>
        <p>이 안내는 나에게만 보여요. 계산값은 각 도구에서 입력해 주세요.</p>
        <nav aria-label="라이더 도구 바로가기">
          {(localHelp==="help"||localHelp==="season-pass")&&<Link href="/tools/season-pass">시즌권 본전 계산 <small>/시즌권</small></Link>}
          {(localHelp==="help"||localHelp==="deck-size")&&<Link href="/tools/deck-size">데크 사이즈 참고 <small>/데크</small></Link>}
        </nav>
        <small>일반 대화와 봇 질문은 공개되므로 연락처·개인정보를 적지 마세요.</small>
      </aside>}

      {/* 실시간 말풍선 피드 영역 */}
      <div className="live-chat-body relative">
        <div
          ref={scrollContainerRef}
          onScroll={handleScroll}
          className="live-chat-feed"
        >
          {messages.length === 0 ? (
            <div className="live-chat-empty">
              <span className="material-symbols-outlined">forum</span>
              <p>실시간 슬로프 현황이나 오늘의 라이딩 잡담을 나눠보세요!</p>
            </div>
          ) : (
            messages.map((msg) => {
              const isMine = currentMember
                ? msg.sender.publicId === currentMember.publicId
                : myGuestMessageIds.has(msg.messageId);
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
                    {isAdmin && !isMine && (
                      <button
                        type="button"
                        onClick={() => void banMessageSender(msg)}
                        title="이 사용자 IP 차단 및 최근 메시지 삭제"
                        className="ml-1 rounded bg-rose-50 px-1.5 py-0.5 text-[10px] font-bold text-rose-600 hover:bg-rose-100"
                      >
                        차단
                      </button>
                    )}
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
            className="live-chat-scroll-btn"
          >
            <span>새 메시지 수신</span>
            <span className="material-symbols-outlined">south</span>
          </button>
        )}

        {/* 도배/링크 차단 개인 에러 토스트 */}
        {toastError && (
          <div className="live-chat-toast-error">
            <span className="material-symbols-outlined">warning</span>
            <span>{toastError}</span>
          </div>
        )}
      </div>

      {/* 하단 일체형 캡슐 입력 폼 바 */}
      <form onSubmit={handleSubmit} className="live-chat-compose unified-capsule-form">
        <div className="capsule-select-wrap">
          <select
            value={canSend ? selectedResort : ""}
            onChange={handleResortChange}
            disabled={!canSend}
            aria-label="리조트 태그"
            className="live-chat-compose-select capsule-select"
          >
            {RESORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        <input
          type="text"
          maxLength={100}
          value={inputContent}
          onChange={(e) => setInputContent(e.target.value)}
          onClick={() => {
            if (!canSend) {
              router.push("/login");
            }
          }}
          onFocus={() => {
            if (!canSend) {
              router.push("/login");
            }
          }}
          placeholder={
            currentMember
              ? "메세지를 입력하세요"
              : anonymousChatEnabled
                ? "로그인 없이 대화할 수 있어요 (닉네임 자동)"
                : "로그인하고 라이브톡에 참여해보세요"
          }
          className="live-chat-compose-input capsule-input"
        />

        <button
          type="submit"
          disabled={Boolean(canSend && !inputContent.trim())}
          aria-label={!canSend ? "로그인" : "메시지 전송"}
          className="live-chat-compose-submit capsule-send-btn"
        >
          {!canSend ? (
            <span>로그인</span>
          ) : (
            <>
              <span className="material-symbols-outlined">arrow_upward</span>
              <span>전송</span>
            </>
          )}
        </button>
      </form>
    </section>
  );
}
