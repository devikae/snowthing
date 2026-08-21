"use client";

import { useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

interface WriterInfo {
  publicId: string | null;
  nickname: string;
  profileImageUrl: string | null;
}

interface PostDetail {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  content: string;
  status: string;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  writer: WriterInfo;
  images: string[];
  createdAt: string;
}

interface CommentItem {
  commentId: number;
  parentId: number | null;
  writerName: string;
  content: string;
  isDeleted: boolean;
  createdAt: string;
  children: CommentItem[];
}

interface CommentListResponse {
  publicId: string;
  totalCommentCount: number;
  comments: CommentItem[];
}

export default function PostDetailPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);
  const router = useRouter();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [totalCommentCount, setTotalCommentCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");

  // 반응 투표 state
  const [reactionMsg, setReactionMsg] = useState("");

  // 원댓글 작성 state
  const [newCommentText, setNewCommentText] = useState("");
  const [isCommentAnon, setIsCommentAnon] = useState(false);
  const [commentAnonPassword, setCommentAnonPassword] = useState("");
  const [submittingComment, setSubmittingComment] = useState(false);

  // 대댓글 작성 폼 열기 target parentId
  const [activeReplyParentId, setActiveReplyParentId] = useState<number | null>(null);
  const [replyText, setReplyText] = useState("");
  const [isReplyAnon, setIsReplyAnon] = useState(false);
  const [replyAnonPassword, setReplyAnonPassword] = useState("");

  const fetchPostDetail = async () => {
    try {
      const res = await fetch(`http://localhost:8080/api/posts/${publicId}`, { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setPost(data);
      } else {
        setErrorMsg("게시글을 찾을 수 없거나 삭제되었습니다.");
      }
    } catch (err) {
      console.error(err);
      setErrorMsg("게시글 로드 실패");
    } finally {
      setLoading(false);
    }
  };

  const fetchComments = async () => {
    try {
      const res = await fetch(`http://localhost:8080/api/posts/${publicId}/comments`, { credentials: "include" });
      if (res.ok) {
        const data: CommentListResponse = await res.json();
        setComments(data.comments || []);
        setTotalCommentCount(data.totalCommentCount || 0);
      }
    } catch (err) {
      console.error("댓글 로드 실패:", err);
    }
  };

  useEffect(() => {
    fetchPostDetail();
    fetchComments();
  }, [publicId]);

  // 추천/비추천 비동기 투표
  const handleReaction = async (type: "LIKE" | "DISLIKE") => {
    setReactionMsg("");
    try {
      const res = await fetch(`http://localhost:8080/api/posts/${publicId}/reactions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ type }),
      });

      if (res.ok) {
        setReactionMsg(`투표 성공! (${type === "LIKE" ? "추천" : "비추천"})`);
        // 화면 Optimistic 또는 재패치
        if (post) {
          if (type === "LIKE") setPost({ ...post, likeCount: post.likeCount + 1 });
          else setPost({ ...post, dislikeCount: post.dislikeCount + 1 });
        }
      } else {
        const errData = await res.json();
        setReactionMsg(`⚠️ ${errData.message || "이미 투표했거나 로그인 필요"}`);
      }
    } catch (err) {
      console.error(err);
      setReactionMsg("⚠️ 서버 통신 오류");
    }
  };

  // 댓글 생성 (원댓글 또는 대댓글)
  const handleCreateComment = async (parentId: number | null) => {
    const text = parentId ? replyText : newCommentText;
    const isAnon = parentId ? isReplyAnon : isCommentAnon;
    const anonPw = parentId ? replyAnonPassword : commentAnonPassword;

    if (!text.trim()) return;

    setSubmittingComment(true);
    try {
      const res = await fetch(`http://localhost:8080/api/posts/${publicId}/comments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          parentId,
          content: text.trim(),
          isAnonymous: isAnon,
          anonymousPassword: isAnon ? anonPw : null,
        }),
      });

      if (res.ok) {
        if (parentId) {
          setReplyText("");
          setActiveReplyParentId(null);
        } else {
          setNewCommentText("");
        }
        fetchComments();
        if (post) setPost({ ...post, commentCount: post.commentCount + 1 });
      } else {
        const err = await res.json();
        alert(`댓글 작성 실패: ${err.message}`);
      }
    } catch (err) {
      console.error(err);
      alert("서버 통신 오류");
    } finally {
      setSubmittingComment(false);
    }
  };

  // 댓글 삭제 (Soft Delete)
  const handleDeleteComment = async (commentId: number, isAnon: boolean) => {
    let anonPw = "";
    if (isAnon) {
      const input = prompt("익명 댓글 삭제 비밀번호를 입력하세요:");
      if (!input) return;
      anonPw = input;
    } else {
      if (!confirm("댓글을 삭제하시겠습니까?")) return;
    }

    try {
      let url = `http://localhost:8080/api/comments/${commentId}`;
      if (anonPw) url += `?anonymousPassword=${encodeURIComponent(anonPw)}`;

      const res = await fetch(url, {
        method: "DELETE",
        credentials: "include",
      });

      if (res.ok) {
        fetchComments();
        if (post) setPost({ ...post, commentCount: Math.max(0, post.commentCount - 1) });
      } else {
        const err = await res.json();
        alert(`삭제 실패: ${err.message}`);
      }
    } catch (err) {
      console.error(err);
      alert("서버 통신 오류");
    }
  };

  if (loading) {
    return <div style={{ padding: "4rem", textAlign: "center", color: "var(--text-muted)", backgroundColor: "var(--bg-dark)", minHeight: "100vh" }}>로딩 중...</div>;
  }

  if (errorMsg || !post) {
    return (
      <div style={{ padding: "4rem", textAlign: "center", color: "var(--error)", backgroundColor: "var(--bg-dark)", minHeight: "100vh" }}>
        ⚠️ {errorMsg || "게시글이 존재하지 않습니다."}
        <div style={{ marginTop: "1rem" }}>
          <Link href="/posts" style={{ color: "var(--primary)" }}>◀ 게시판 목록으로 돌아가기</Link>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "var(--bg-dark)", color: "var(--text-main)" }}>
      {/* Header */}
      <header style={{ borderBottom: "1px solid var(--border-dark)", background: "var(--bg-dark-soft)", padding: "1rem 2rem" }}>
        <div className="container" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Link href="/posts" style={{ fontSize: "1.5rem", fontWeight: "bold", color: "var(--primary)", textDecoration: "none" }}>
            🏂 Snowthing Board
          </Link>
          <Link href="/posts" style={{ color: "var(--text-sub)", textDecoration: "none" }}>
            ◀ 목록으로 돌아가기
          </Link>
        </div>
      </header>

      <main className="container" style={{ paddingTop: "2rem", paddingBottom: "4rem", maxWidth: "900px" }}>
        {/* Post Main Card */}
        <article className="card-supabase" style={{ marginBottom: "2rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.75rem" }}>
            <span style={{ color: "var(--primary)", fontSize: "0.9rem", fontWeight: 600 }}>[{post.categoryName}]</span>
            <span style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>{new Date(post.createdAt).toLocaleString()}</span>
          </div>

          <h1 style={{ fontSize: "1.8rem", fontWeight: "bold", marginBottom: "1rem", lineHeight: 1.3 }}>{post.title}</h1>

          <div style={{ display: "flex", justifyContent: "space-between", borderBottom: "1px solid var(--border-dark)", paddingBottom: "1rem", marginBottom: "1.5rem", color: "var(--text-sub)", fontSize: "0.9rem" }}>
            <div>작성자: <strong style={{ color: "var(--text-main)" }}>{post.writer.nickname}</strong></div>
            <div>조회수: {post.viewCount} | 댓글: {post.commentCount}</div>
          </div>

          {/* Post Content */}
          <div style={{ minHeight: "150px", lineHeight: 1.7, fontSize: "1.05rem", whiteSpace: "pre-wrap", marginBottom: "2rem" }}>
            {post.content}
          </div>

          {/* Attached Images */}
          {post.images && post.images.length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: "1rem", marginBottom: "2rem" }}>
              {post.images.map((img, idx) => (
                <img key={idx} src={img} alt="첨부 이미지" style={{ maxWidth: "100%", borderRadius: "8px", border: "1px solid var(--border-dark)" }} />
              ))}
            </div>
          )}

          {/* Reaction Buttons */}
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "0.5rem", borderTop: "1px solid var(--border-dark)", paddingTop: "1.5rem" }}>
            <div style={{ display: "flex", gap: "1rem" }}>
              <button
                onClick={() => handleReaction("LIKE")}
                style={{
                  padding: "0.75rem 1.5rem",
                  borderRadius: "20px",
                  border: "1px solid var(--primary)",
                  backgroundColor: "var(--bg-dark-card)",
                  color: "var(--primary)",
                  cursor: "pointer",
                  fontSize: "1rem",
                  fontWeight: 600,
                }}
              >
                👍 추천 {post.likeCount}
              </button>
              <button
                onClick={() => handleReaction("DISLIKE")}
                style={{
                  padding: "0.75rem 1.5rem",
                  borderRadius: "20px",
                  border: "1px solid var(--border-dark)",
                  backgroundColor: "var(--bg-dark-card)",
                  color: "var(--text-sub)",
                  cursor: "pointer",
                  fontSize: "1rem",
                  fontWeight: 600,
                }}
              >
                👎 비추천 {post.dislikeCount}
              </button>
            </div>
            {reactionMsg && <div style={{ fontSize: "0.85rem", color: "var(--primary)", marginTop: "0.5rem" }}>{reactionMsg}</div>}
          </div>
        </article>

        {/* Comment Section Header */}
        <section className="card-supabase">
          <h2 style={{ fontSize: "1.3rem", fontWeight: "bold", marginBottom: "1.5rem", display: "flex", alignItems: "center", gap: "0.5rem" }}>
            💬 댓글 <span style={{ color: "var(--primary)" }}>{totalCommentCount}</span>
          </h2>

          {/* Root Comment Input Form */}
          <div style={{ marginBottom: "2rem", borderBottom: "1px solid var(--border-dark)", paddingBottom: "1.5rem" }}>
            <textarea
              rows={3}
              placeholder="댓글을 작성해 보세요..."
              value={newCommentText}
              onChange={(e) => setNewCommentText(e.target.value)}
              className="input-supabase"
              style={{ width: "100%", marginBottom: "0.75rem" }}
            />
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <input
                  type="checkbox"
                  id="cAnon"
                  checked={isCommentAnon}
                  onChange={(e) => setIsCommentAnon(e.target.checked)}
                />
                <label htmlFor="cAnon" style={{ fontSize: "0.85rem", color: "var(--text-sub)", cursor: "pointer" }}>익명 작성</label>
                {isCommentAnon && (
                  <input
                    type="password"
                    placeholder="비밀번호"
                    value={commentAnonPassword}
                    onChange={(e) => setCommentAnonPassword(e.target.value)}
                    className="input-supabase"
                    style={{ padding: "0.25rem 0.5rem", fontSize: "0.85rem", width: "120px" }}
                  />
                )}
              </div>
              <button
                disabled={submittingComment}
                onClick={() => handleCreateComment(null)}
                className="btn-primary-green"
                style={{ padding: "0.5rem 1rem", fontSize: "0.9rem" }}
              >
                댓글 등록
              </button>
            </div>
          </div>

          {/* Comment Tree List Rendering */}
          <div>
            {comments.length === 0 ? (
              <div style={{ textAlign: "center", color: "var(--text-muted)", padding: "2rem 0" }}>첫 번째 댓글을 작성해 보세요!</div>
            ) : (
              comments.map((item) => (
                <RenderCommentItem
                  key={item.commentId}
                  item={item}
                  activeReplyParentId={activeReplyParentId}
                  setActiveReplyParentId={setActiveReplyParentId}
                  replyText={replyText}
                  setReplyText={setReplyText}
                  isReplyAnon={isReplyAnon}
                  setIsReplyAnon={setIsReplyAnon}
                  replyAnonPassword={replyAnonPassword}
                  setReplyAnonPassword={setReplyAnonPassword}
                  handleCreateComment={handleCreateComment}
                  handleDeleteComment={handleDeleteComment}
                />
              ))
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

// 댓글 및 자식 대댓글 트리 재귀/계층형 컴포넌트
function RenderCommentItem({
  item,
  depth = 0,
  activeReplyParentId,
  setActiveReplyParentId,
  replyText,
  setReplyText,
  isReplyAnon,
  setIsReplyAnon,
  replyAnonPassword,
  setReplyAnonPassword,
  handleCreateComment,
  handleDeleteComment,
}: {
  item: CommentItem;
  depth?: number;
  activeReplyParentId: number | null;
  setActiveReplyParentId: (id: number | null) => void;
  replyText: string;
  setReplyText: (text: string) => void;
  isReplyAnon: boolean;
  setIsReplyAnon: (val: boolean) => void;
  replyAnonPassword: string;
  setReplyAnonPassword: (val: string) => void;
  handleCreateComment: (parentId: number | null) => void;
  handleDeleteComment: (commentId: number, isAnon: boolean) => void;
}) {
  return (
    <div
      style={{
        marginLeft: depth > 0 ? `${depth * 1.5}rem` : "0",
        borderLeft: depth > 0 ? "2px solid var(--primary)" : "none",
        paddingLeft: depth > 0 ? "1rem" : "0",
        marginBottom: "1rem",
        paddingBottom: "0.75rem",
        borderBottom: depth === 0 ? "1px solid var(--border-dark)" : "none",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.4rem" }}>
        <span style={{ fontWeight: 600, fontSize: "0.9rem", color: item.isDeleted ? "var(--text-muted)" : "var(--text-main)" }}>
          {depth > 0 && "↳ "} {item.writerName}
        </span>
        <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
          {new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
        </span>
      </div>

      <p style={{ fontSize: "0.95rem", color: item.isDeleted ? "var(--text-muted)" : "var(--text-main)", fontStyle: item.isDeleted ? "italic" : "normal", marginBottom: "0.5rem" }}>
        {item.content}
      </p>

      {!item.isDeleted && (
        <div style={{ display: "flex", gap: "1rem", fontSize: "0.8rem" }}>
          <button
            onClick={() => setActiveReplyParentId(activeReplyParentId === item.commentId ? null : item.commentId)}
            style={{ background: "none", border: "none", color: "var(--primary)", cursor: "pointer", padding: 0 }}
          >
            {activeReplyParentId === item.commentId ? "답글 취소" : "답글 달기"}
          </button>
          <button
            onClick={() => handleDeleteComment(item.commentId, item.writerName.includes("익명"))}
            style={{ background: "none", border: "none", color: "var(--error)", cursor: "pointer", padding: 0 }}
          >
            삭제
          </button>
        </div>
      )}

      {/* Reply Input Form */}
      {activeReplyParentId === item.commentId && (
        <div style={{ marginTop: "0.75rem", background: "var(--bg-dark-card)", padding: "0.75rem", borderRadius: "6px" }}>
          <textarea
            rows={2}
            placeholder={`${item.writerName}님에게 답글 작성...`}
            value={replyText}
            onChange={(e) => setReplyText(e.target.value)}
            className="input-supabase"
            style={{ width: "100%", marginBottom: "0.5rem" }}
          />
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <input
                type="checkbox"
                id={`rAnon_${item.commentId}`}
                checked={isReplyAnon}
                onChange={(e) => setIsReplyAnon(e.target.checked)}
              />
              <label htmlFor={`rAnon_${item.commentId}`} style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>익명</label>
              {isReplyAnon && (
                <input
                  type="password"
                  placeholder="비밀번호"
                  value={replyAnonPassword}
                  onChange={(e) => setReplyAnonPassword(e.target.value)}
                  className="input-supabase"
                  style={{ padding: "0.2rem 0.4rem", fontSize: "0.8rem", width: "100px" }}
                />
              )}
            </div>
            <button
              onClick={() => handleCreateComment(item.commentId)}
              className="btn-primary-green"
              style={{ padding: "0.3rem 0.8rem", fontSize: "0.85rem" }}
            >
              답글 등록
            </button>
          </div>
        </div>
      )}

      {/* Render Nested Children */}
      {item.children && item.children.length > 0 && (
        <div style={{ marginTop: "0.75rem" }}>
          {item.children.map((child) => (
            <RenderCommentItem
              key={child.commentId}
              item={child}
              depth={depth + 1}
              activeReplyParentId={activeReplyParentId}
              setActiveReplyParentId={setActiveReplyParentId}
              replyText={replyText}
              setReplyText={setReplyText}
              isReplyAnon={isReplyAnon}
              setIsReplyAnon={setIsReplyAnon}
              replyAnonPassword={replyAnonPassword}
              setReplyAnonPassword={setReplyAnonPassword}
              handleCreateComment={handleCreateComment}
              handleDeleteComment={handleDeleteComment}
            />
          ))}
        </div>
      )}
    </div>
  );
}
