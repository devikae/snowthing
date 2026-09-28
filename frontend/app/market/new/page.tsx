"use client";

import dynamic from "next/dynamic";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useRef, useState } from "react";
import { Footer, TopNav } from "../../components/SiteChrome";
import type { ToastEditorHandle } from "../../components/ToastEditor";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";

const ToastEditor = dynamic(() => import("../../components/ToastEditor"), { ssr: false });

interface MarketCategory {
  code: string;
  name: string;
}

interface UploadedImage {
  imageUrl: string;
  imageKey: string;
}

const productConditions = [
  ["NEW", "새 상품"],
  ["LIKE_NEW", "거의 새 상품"],
  ["GOOD", "사용감 적음"],
  ["USED", "사용감 있음"],
  ["DAMAGED", "수리·하자 있음"],
] as const;

const transactionMethods = [
  ["DIRECT", "직거래"],
  ["DELIVERY", "택배"],
  ["BOTH", "모두 가능"],
] as const;

export default function MarketCreatePage() {
  const router = useRouter();
  const editorRef = useRef<ToastEditorHandle>(null);
  const [categories, setCategories] = useState<MarketCategory[]>([]);
  const [categoryCode, setCategoryCode] = useState("");
  const [title, setTitle] = useState("");
  const [productCondition, setProductCondition] = useState("GOOD");
  const [transactionMethod, setTransactionMethod] = useState("BOTH");
  const [price, setPrice] = useState("");
  const [negotiable, setNegotiable] = useState(false);
  const [free, setFree] = useState(false);
  const [contact, setContact] = useState("");
  const [images, setImages] = useState<UploadedImage[]>([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    void (async () => {
      const [memberResponse, categoryResponse] = await Promise.all([
        fetch(API_ENDPOINTS.members.me, { credentials: "include" }),
        fetch(API_ENDPOINTS.market.categories, { credentials: "include" }),
      ]);
      if (memberResponse.status === 401 || categoryResponse.status === 401) {
        router.replace(`/login?returnUrl=${encodeURIComponent("/market/new")}`);
        return;
      }
      if (!categoryResponse.ok) {
        setError("상품 분류를 불러오지 못했습니다.");
        return;
      }
      const data: { categories?: MarketCategory[] } = await categoryResponse.json();
      const nextCategories = Array.isArray(data.categories) ? data.categories : [];
      setCategories(nextCategories);
      setCategoryCode(nextCategories[0]?.code ?? "");
    })();
  }, [router]);

  const uploadImages = async (files: FileList | null) => {
    if (!files?.length) return;
    if (images.length + files.length > 5) {
      setError("사진은 최대 5장까지 등록할 수 있습니다.");
      return;
    }
    setUploading(true);
    setError("");
    try {
      const uploaded: UploadedImage[] = [];
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append("file", file);
        const response = await csrfFetch(API_ENDPOINTS.images.upload, { method: "POST", body: formData });
        if (!response.ok) {
          const data = await response.json();
          throw new Error(data.message || "이미지 업로드에 실패했습니다.");
        }
        uploaded.push(await response.json());
      }
      setImages((current) => [...current, ...uploaded]);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const content = editorRef.current?.getInstance().getMarkdown()?.trim() ?? "";
    if (!categoryCode || !title.trim() || !content || !contact.trim()) {
      setError("상품 분류, 제목, 본문, 연락처를 모두 입력해 주세요.");
      return;
    }
    const numericPrice = free ? 0 : Number(price);
    if (!free && (!Number.isInteger(numericPrice) || numericPrice < 1 || numericPrice > 20_000_000)) {
      setError("가격은 1원 이상 2,000만원 이하로 입력해 주세요.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.market.create, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryCode,
          title: title.trim(),
          content,
          productCondition,
          transactionMethod,
          price: numericPrice,
          negotiable: free ? false : negotiable,
          free,
          contact: contact.trim(),
          imageUrls: images.map((image) => image.imageUrl),
        }),
      });
      if (response.status === 401) {
        router.replace(`/login?returnUrl=${encodeURIComponent("/market/new")}`);
        return;
      }
      if (!response.ok) {
        const data = await response.json();
        throw new Error(data.message || "판매글을 등록하지 못했습니다.");
      }
      const data: { publicId: string } = await response.json();
      router.push(`/market/${data.publicId}`);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "판매글을 등록하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="community-page">
      <TopNav active="market" />
      <main className="community-container market-compose-page">
        <header className="market-compose-heading"><div><Link href="/market">중고장터</Link><span>/</span><strong>판매글 등록</strong></div><h1>판매할 상품 정보를 입력해 주세요.</h1></header>
        <form className="market-compose-card" onSubmit={submit}>
          {error && <div className="compose-error" role="alert"><span className="material-symbols-outlined">error</span>{error}</div>}
          <div className="market-form-grid">
            <label><span>상품 분류 *</span><select value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)} required>{categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label>
            <label><span>상품 상태 *</span><select value={productCondition} onChange={(event) => setProductCondition(event.target.value)}>{productConditions.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
          </div>
          <fieldset className="market-radio-field"><legend>거래 방식 *</legend>{transactionMethods.map(([code, label]) => <label key={code}><input type="radio" name="transactionMethod" value={code} checked={transactionMethod === code} onChange={() => setTransactionMethod(code)} />{label}</label>)}</fieldset>
          <label className="market-form-field"><span>글 제목 *</span><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} placeholder="판매할 상품을 알 수 있는 제목을 입력해 주세요." required /></label>
          <div className="market-price-row">
            <label><span>가격 *</span><div><input type="number" min={1} max={20_000_000} value={free ? "" : price} onChange={(event) => setPrice(event.target.value)} disabled={free} placeholder="0" /><b>원</b></div></label>
            <label className="market-check"><input type="checkbox" checked={negotiable} onChange={(event) => setNegotiable(event.target.checked)} disabled={free} />가격 협의 가능</label>
            <label className="market-check"><input type="checkbox" checked={free} onChange={(event) => { setFree(event.target.checked); if (event.target.checked) setNegotiable(false); }} />무료 나눔</label>
          </div>
          <label className="market-form-field"><span>연락처 * <small>전화번호나 카카오톡 ID 등 자유 입력, 최대 30자</small></span><input value={contact} onChange={(event) => setContact(event.target.value)} maxLength={30} placeholder="구매 희망자가 연락할 수단을 입력해 주세요." required /></label>
          <section className="market-editor-field"><span>본문 *</span><p>상품 상태, 사용 기간, 하자 여부, 거래 조건은 판매자가 직접 확인해 작성해 주세요.</p><div className="compose-editor"><ToastEditor ref={editorRef} height="440px" /></div></section>
          <section className="market-image-field"><div><span>사진 <small>선택, 최대 5장</small></span><b>{images.length}/5</b></div><input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={uploading || images.length >= 5} onChange={(event) => void uploadImages(event.target.files)} />{images.length > 0 && <div className="market-image-previews">{images.map((image, index) => <div key={image.imageKey}><Image src={image.imageUrl} alt={`첨부 이미지 ${index + 1}`} width={120} height={90} unoptimized /><button type="button" onClick={() => setImages((current) => current.filter((_, itemIndex) => itemIndex !== index))} aria-label={`${index + 1}번째 이미지 제거`}><span className="material-symbols-outlined">close</span></button></div>)}</div>}</section>
          <div className="market-compose-actions"><Link href="/market">취소</Link><button type="submit" disabled={submitting || uploading}>{submitting ? "등록 중..." : "판매글 등록"}</button></div>
        </form>
      </main>
      <Footer />
    </div>
  );
}
