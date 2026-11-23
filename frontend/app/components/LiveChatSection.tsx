"use client";

import React, { useEffect, useRef, useState } from "react";
import { CommentProfileAvatar } from "./CommentProfileAvatar";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Client } from "@stomp/stompjs";
import { API_BASE_URL, API_ENDPOINTS } from "../lib/api";
import { RESORT_MAP, RESORT_OPTIONS } from "../lib/resortTags";
import { localChatCommand, type LocalChatCommand } from "../lib/chatCommands";
import { csrfFetch } from "../lib/csrfFetch";
import { formatKoreanDate, toDate } from "../lib/koreanDate";

export interface MemberProfile {
  publicId: string;
  email: string;
  nickname: string;
  role?: string;
}

interface ChatSender {
  publicId: string;
  nickname: string;
  profileImageUrl?: string | null;
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

const BAN_DAY_OPTIONS = [1, 7, 30] as const;
type BanDays = (typeof BAN_DAY_OPTIONS)[number];
const BOT_SENDER_ID = "snowthing-ai";

// 로그인 회원이 보낸 메시지만 회원 정지를 걸 수 있다. 비회원은 매번 anon- 아이디, 안내 봇은 고정 아이디다.
function isMemberSender(sender: ChatSender): boolean {
  return !sender.publicId.startsWith("anon-") && sender.publicId !== BOT_SENDER_ID;
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

const CHAT_DATE_TIME: Intl.DateTimeFormatOptions = { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23" };

function formatRelativeTime(isoString: string): string {
  try {
    const sent = toDate(isoString);
    const now = new Date();
    const diffSec = Math.floor((now.getTime() - sent.getTime()) / 1000);

    if (diffSec < 10) return "방금";
    if (diffSec < 60) return `${diffSec}초 전`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}분 전`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;

    // 하루가 지난 메시지는 시각만으로는 언제인지 알 수 없어 KST 날짜를 붙인다. 예: 11월 10일 21:04
    return formatKoreanDate(sent, CHAT_DATE_TIME);
  } catch {
    return "방금";
  }
}

interface LiveChatSectionProps {
  currentMember: MemberProfile | null;
}

// 바닥에서 이만큼 안이면 바닥에 붙어 있는 것으로 보고 새 메시지에 따라 내린다.
const BOTTOM_STICK_PX = 120;

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
  // 관리자가 라이브톡을 닫으면 false다 (IKA-115). 관리자는 닫힌 동안에도 안내를 보낼 수 있다.
  const [chatEnabled, setChatEnabled] = useState(true);
  const [myGuestMessageIds, setMyGuestMessageIds] = useState<Set<string>>(() => new Set());
  // 비회원 닉네임은 서버가 IP로 정한다. 내가 보낸 메시지가 돌아왔을 때 그 이름을 받아 둬야 본인이 자기 이름을 안다.
  const [myGuestNickname, setMyGuestNickname] = useState<string | null>(null);
  const pendingGuestSendsRef = useRef<{ content: string; time: number }[]>([]);
  const memberPublicId = currentMember?.publicId;
  const isAdmin = currentMember?.role === "ROLE_ADMIN";
  const chatClosed = !chatEnabled && !isAdmin;
  const needsLogin = !currentMember && !anonymousChatEnabled;
  const canSend = !needsLogin && !chatClosed;
  const [banTargetId, setBanTargetId] = useState<string | null>(null);
  const [banDays, setBanDays] = useState<BanDays>(7);
  const [banSuspend, setBanSuspend] = useState(false);
  const [banBusy, setBanBusy] = useState(false);
  const [hidingId, setHidingId] = useState<string | null>(null);

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
    const atBottom = distanceToBottom < BOTTOM_STICK_PX;
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
      .then((config: { anonymousChatEnabled?: boolean; chatEnabled?: boolean } | null) => {
        if (!cancelled && config) {
          setAnonymousChatEnabled(config.anonymousChatEnabled === true);
          setChatEnabled(config.chatEnabled !== false);
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  // 비회원 닉네임은 서버가 IP로 정한다. 보내기 전에 미리 받아 두면 처음부터 내 이름을 알고, 새로고침해도 내 메시지를 알아본다 (IKA-115).
  useEffect(() => {
    if (memberPublicId) return;
    let cancelled = false;
    fetch(API_ENDPOINTS.chat.me, { credentials: "include" })
      .then((response) => (response.ok ? response.json() : null))
      .then((me: { guestNickname?: string | null } | null) => {
        if (!cancelled && me?.guestNickname) setMyGuestNickname(me.guestNickname);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [memberPublicId]);

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
              if (payload.key === "CHAT_ENABLED") setChatEnabled(payload.value === true);
              return;
            }
            receiveMessage(payload as ChatMessage);
          } catch {
            // ignore parse error
          }
        });

        // 금칙어에 걸린 내 메시지는 나에게만 이 큐로 온다. 다른 사람에게는 가지 않지만 내 화면에는 평소처럼 그린다 (IKA-115).
        client.subscribe("/user/queue/chat", (frame) => {
          try {
            receiveMessage(JSON.parse(frame.body) as ChatMessage);
          } catch {
            // ignore parse error
          }
        });

        function receiveMessage(newMsg: ChatMessage) {
          // 비회원 메시지는 매번 새 ID로 오므로 방금 보낸 내용과 맞춰 내 메시지로 표시한다.
          const pendingIndex = pendingGuestSendsRef.current.findIndex(
            (pending) => pending.content === newMsg.content && Date.now() - pending.time < GUEST_ECHO_WINDOW_MILLIS,
          );
          const mine =
            (memberPublicId !== undefined && newMsg.sender.publicId === memberPublicId) ||
            (pendingIndex !== -1 && newMsg.sender.publicId.startsWith("anon-"));
          // 새 메시지를 그리기 전에 바닥 근처였는지 본다. 그린 뒤에는 메시지 높이만큼 멀어져 있다.
          const feed = scrollContainerRef.current;
          const stick =
            mine || !feed || feed.scrollHeight - feed.scrollTop - feed.clientHeight < BOTTOM_STICK_PX;
          if (pendingIndex !== -1 && newMsg.sender.publicId.startsWith("anon-")) {
            pendingGuestSendsRef.current.splice(pendingIndex, 1);
            setMyGuestMessageIds((previous) => new Set(previous).add(newMsg.messageId));
            // IP가 바뀌면(휴대폰 등) 이름도 바뀌므로 가장 최근 이름으로 둔다.
            setMyGuestNickname(newMsg.sender.nickname);
          }
          setMessages((previous) => mergeMessages(previous, [newMsg]));

          // 바닥 근처였거나 내가 보낸 메시지면 바로 내린다. 부드러운 스크롤은 움직이는 중에 다음 메시지가 오면
          // 바닥이 아니라고 판단해 멈췄다. 그래서 새 메시지는 즉시 내린다.
          setTimeout(() => {
            if (stick) {
              scrollToBottom("auto");
            } else {
              setShowScrollBottom(true);
            }
          }, 50);
        }

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
  }, [memberPublicId]);

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
    if (chatClosed) return;
    if (needsLogin) {
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

  const openBanPanel = (message: ChatMessage) => {
    setBanTargetId(message.messageId);
    setBanDays(7);
    setBanSuspend(false);
  };

  const closeBanPanel = () => {
    if (!banBusy) setBanTargetId(null);
  };

  // 한 줄만 가린다 (IKA-115). 보낸 사람은 막지 않는다. 서버가 모든 화면에 지우라고 방송하므로 내 화면도 같은 방송으로 지워진다.
  const hideMessage = async (message: ChatMessage) => {
    if (hidingId) return;
    setHidingId(message.messageId);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.hideChatMessage(message.messageId), { method: "DELETE" });
      if (!response.ok) {
        setToastError("메시지를 가리지 못했습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setMessages((previous) => previous.filter((item) => item.messageId !== message.messageId));
      if (banTargetId === message.messageId) setBanTargetId(null);
    } catch {
      setToastError("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setHidingId(null);
    }
  };

  // IP 차단(최근 메시지 삭제 포함)을 먼저 하고, 고른 경우에만 회원 정지를 이어서 한다. 둘 다 서버 관리자 API가 권한을 다시 확인한다.
  const banMessageSender = async (message: ChatMessage) => {
    if (banBusy) return;
    setBanBusy(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.banMessageIp(message.messageId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ days: banDays }),
      });
      if (!response.ok) {
        setToastError("IP를 차단하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setMessages((previous) => previous.filter((item) => item.messageId !== message.messageId));
      if (banSuspend && isMemberSender(message.sender)) {
        const suspend = await csrfFetch(API_ENDPOINTS.admin.memberStatusAction(message.sender.publicId, "suspend"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ days: banDays, reason: "라이브톡 차단과 함께 정지" }),
        });
        if (!suspend.ok) {
          const data = await suspend.json().catch(() => null);
          setToastError(`IP는 차단했지만 회원을 정지하지 못했습니다. ${data?.message ?? ""}`.trim());
        }
      }
      setBanTargetId(null);
    } catch {
      setToastError("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBanBusy(false);
    }
  };

  return (
    <section aria-label="실시간 라이브톡" className="live-chat-card relative">
      {/* 상단 헤더 바 */}
      <div className="live-chat-head">
        <div className="section-head-title">
          <h2>라이브톡</h2>
          {/* 비회원은 제목 바로 아래에 자기 닉네임을 작게 둔다 (IKA-115). 폰 폭에서도 잘리지 않고 화면마다 배치가 같다. */}
          {!currentMember && anonymousChatEnabled && chatEnabled && myGuestNickname && (
            <small className="text-xs text-slate-500">
              내 닉네임 <strong className="font-bold text-slate-800">{myGuestNickname}</strong>
              <span className="ml-1 text-slate-400">(접속한 인터넷이 바뀌면 이름도 바뀌어요)</span>
            </small>
          )}
        </div>
      </div>

      {localHelp&&<aside id="live-chat-local-help" className="live-chat-local-help" aria-label="라이브톡 도구 안내">
        <button type="button" className="live-chat-local-help-close" aria-label="안내 닫기" onClick={()=>setLocalHelp(null)}><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">close</span></button>
        <p>이 안내는 나에게만 보여요. 계산값은 각 도구에서 입력해 주세요.</p>
        <nav aria-label="라이더 도구 바로가기">
          {(localHelp==="help"||localHelp==="season-pass")&&<Link href="/tools/season-pass">시즌권 본전 계산 <small>/시즌권</small></Link>}
          {(localHelp==="help"||localHelp==="deck-size")&&<Link href="/tools/deck-size">데크 사이즈 참고 <small>/데크</small></Link>}
        </nav>
        <small>일반 대화와 봇 질문은 공개되므로 연락처, 개인정보를 적지 마세요.</small>
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
              <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">forum</span>
              <p>실시간 슬로프 현황이나 오늘의 라이딩 잡담을 나눠보세요!</p>
            </div>
          ) : (
            messages.map((msg) => {
              // 비회원은 같은 IP면 늘 같은 닉네임이라 닉네임으로 내 메시지를 알아본다. 같은 와이파이를 쓰는 사람과는 원래 이름이 같다.
              const isMine = currentMember
                ? msg.sender.publicId === currentMember.publicId
                : myGuestMessageIds.has(msg.messageId) ||
                  (myGuestNickname !== null && msg.sender.publicId.startsWith("anon-") && msg.sender.nickname === myGuestNickname);
              const resort = msg.resortTag ? RESORT_MAP[msg.resortTag] : null;
              return (
                <div key={msg.messageId} className={`live-chat-message ${isMine ? "mine" : "other"}`}>
                  <div className="live-chat-message-meta">
                    {!isMine && <CommentProfileAvatar nickname={msg.sender.nickname} profileImageUrl={msg.sender.profileImageUrl} className="live-chat-avatar" size={24} />}
                    {!isMine && <strong>{msg.sender.nickname}</strong>}
                    {isMine && !currentMember && <strong>{msg.sender.nickname} (나)</strong>}
                    <span className="live-chat-message-tag">
                      <i className={`rounded-full ${resort ? resort.markerClass : "bg-slate-400"}`} />
                      {resort ? resort.koreanName : "일반"}
                    </span>
                    <time>{formatRelativeTime(msg.sentAt)}</time>
                    {isAdmin && !isMine && (
                      <button
                        type="button"
                        onClick={() => void hideMessage(msg)}
                        disabled={hidingId === msg.messageId}
                        title="이 메시지만 모든 화면에서 지웁니다. 보낸 사람은 막지 않습니다."
                        className="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-600 hover:bg-slate-200 disabled:opacity-60"
                      >
                        {hidingId === msg.messageId ? "가리는 중" : "가리기"}
                      </button>
                    )}
                    {isAdmin && !isMine && (
                      <button
                        type="button"
                        onClick={() => (banTargetId === msg.messageId ? closeBanPanel() : openBanPanel(msg))}
                        title="이 사용자 IP 차단 및 최근 메시지 삭제"
                        aria-expanded={banTargetId === msg.messageId}
                        className="ml-1 rounded bg-rose-50 px-1.5 py-0.5 text-[10px] font-bold text-rose-600 hover:bg-rose-100"
                      >
                        차단
                      </button>
                    )}
                  </div>
                  <div className="live-chat-message-bubble">{msg.content}</div>
                  {isAdmin && banTargetId === msg.messageId && (
                    <ChatBanPanel
                      nickname={msg.sender.nickname}
                      canSuspend={isMemberSender(msg.sender)}
                      days={banDays}
                      setDays={setBanDays}
                      suspend={banSuspend}
                      setSuspend={setBanSuspend}
                      busy={banBusy}
                      onConfirm={() => void banMessageSender(msg)}
                      onClose={closeBanPanel}
                    />
                  )}
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
            <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">south</span>
          </button>
        )}

        {/* 도배/링크 차단 개인 에러 토스트 */}
        {toastError && (
          <div className="live-chat-toast-error">
            <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">warning</span>
            <span>{toastError}</span>
          </div>
        )}
      </div>

      {!chatEnabled && (
        <p role="status" className="mx-3 mb-1 rounded-lg bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-800">
          {isAdmin ? "라이브톡이 닫혀 있습니다. 관리자만 메시지를 보낼 수 있습니다." : "라이브톡이 잠시 닫혀 있어요. 잠시 후 다시 와 주세요."}
        </p>
      )}

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
            if (needsLogin) {
              router.push("/login");
            }
          }}
          onFocus={() => {
            if (needsLogin) {
              router.push("/login");
            }
          }}
          disabled={chatClosed}
          placeholder={
            chatClosed
              ? "라이브톡이 잠시 닫혀 있어요"
              : currentMember
              ? "메시지를 입력하세요"
              : anonymousChatEnabled
                ? myGuestNickname
                  ? `${myGuestNickname}(으)로 대화 중이에요`
                  : "로그인 없이 대화할 수 있어요 (닉네임 자동)"
                : "로그인하고 라이브톡에 참여해 보세요"
          }
          className="live-chat-compose-input capsule-input"
          aria-label="라이브톡 메시지"
        />

        <button
          type="submit"
          disabled={chatClosed || Boolean(canSend && !inputContent.trim())}
          aria-label={needsLogin ? "로그인" : "메시지 전송"}
          className="live-chat-compose-submit capsule-send-btn"
        >
          {needsLogin ? (
            <span>로그인</span>
          ) : (
            <>
              <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">arrow_upward</span>
              <span>전송</span>
            </>
          )}
        </button>
      </form>
    </section>
  );
}

function ChatBanPanel({
  nickname,
  canSuspend,
  days,
  setDays,
  suspend,
  setSuspend,
  busy,
  onConfirm,
  onClose,
}: {
  nickname: string;
  canSuspend: boolean;
  days: BanDays;
  setDays: (days: BanDays) => void;
  suspend: boolean;
  setSuspend: (value: boolean) => void;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-label={`${nickname} 차단`}
      onKeyDown={(event) => {
        if (event.key === "Escape") onClose();
      }}
      className="mt-1 grid max-w-[280px] gap-2 rounded-lg border border-rose-200 bg-white p-3 text-xs text-slate-700 shadow-sm"
    >
      <p className="font-bold">{nickname}의 IP를 차단하고 최근 메시지를 지웁니다.</p>
      <fieldset className="flex flex-wrap gap-3">
        <legend className="mb-1 text-[11px] text-slate-500">차단 기간</legend>
        {BAN_DAY_OPTIONS.map((option) => (
          <label key={option} className="inline-flex items-center gap-1">
            <input
              type="radio"
              name="chat-ban-days"
              value={option}
              checked={days === option}
              disabled={busy}
              onChange={() => setDays(option)}
              autoFocus={days === option}
            />
            {option}일
          </label>
        ))}
      </fieldset>
      {canSuspend && (
        <label className="inline-flex items-center gap-1">
          <input type="checkbox" checked={suspend} disabled={busy} onChange={(event) => setSuspend(event.target.checked)} />
          회원 정지도 함께 하기
        </label>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onClose} disabled={busy} className="rounded border border-slate-200 px-2 py-1">
          취소
        </button>
        <button type="button" onClick={onConfirm} disabled={busy} className="rounded bg-rose-600 px-2 py-1 font-bold text-white disabled:opacity-60">
          {busy ? "차단 중" : "차단"}
        </button>
      </div>
    </div>
  );
}
