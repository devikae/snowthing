"use client";

import dynamic from "next/dynamic";
import Image from "next/image";
import Link from "next/link";
import { use, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../../components/SiteChrome";
import type { ToastEditorHandle } from "../../../components/ToastEditor";
import { API_ENDPOINTS } from "../../../lib/api";
import { csrfFetch } from "../../../lib/csrfFetch";

const ToastEditor = dynamic(() => import("../../../components/ToastEditor"), { ssr: false });

interface Category { code: string; name: string }
interface Detail {
  title: string;
  content: string;
  category: Category;
  productCondition: string;
  transactionMethod: string;
  price: number;
  negotiable: boolean;
  free: boolean;
  contact: string;
  images: string[];
  version: number;
  canEdit: boolean;
}

const conditions = [["NEW", "새 상품"], ["LIKE_NEW", "거의 새 상품"], ["GOOD", "사용감 적음"], ["USED", "사용감 있음"], ["DAMAGED", "수리·하자 있음"]] as const;
const methods = [["DIRECT", "직거래"], ["DELIVERY", "택배"], ["BOTH", "모두 가능"]] as const;

export default function MarketEditPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);
  const router = useRouter();
  const editorRef = useRef<ToastEditorHandle>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [categoryCode, setCategoryCode] = useState("");
  const [title, setTitle] = useState("");
  const [productCondition, setProductCondition] = useState("GOOD");
  const [transactionMethod, setTransactionMethod] = useState("BOTH");
  const [price, setPrice] = useState("");
  const [negotiable, setNegotiable] = useState(false);
  const [free, setFree] = useState(false);
  const [contact, setContact] = useState("");
  const [images, setImages] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void (async () => {
      const [detailResponse, categoryResponse] = await Promise.all([
        fetch(API_ENDPOINTS.market.detail(publicId), { credentials: "include" }),
        fetch(API_ENDPOINTS.market.categories, { credentials: "include" }),
      ]);
      if (detailResponse.status === 401 || categoryResponse.status === 401) {
        router.replace(`/login?returnUrl=${encodeURIComponent(`/market/${publicId}/edit`)}`);
        return;
      }
      if (!detailResponse.ok || !categoryResponse.ok) {
        setError("판매글 정보를 불러오지 못했습니다.");
        return;
      }
      const listing: Detail = await detailResponse.json();
      if (!listing.canEdit) {
        router.replace(`/market/${publicId}`);
        return;
      }
      const categoryData: { categories: Category[] } = await categoryResponse.json();
      setCategories(categoryData.categories ?? []);
      setDetail(listing);
      setCategoryCode(listing.category.code);
      setTitle(listing.title);
      setProductCondition(listing.productCondition);
      setTransactionMethod(listing.transactionMethod);
      setPrice(String(listing.price));
      setNegotiable(listing.negotiable);
      setFree(listing.free);
      setContact(listing.contact);
      setImages(listing.images);
    })();
  }, [publicId, router]);

  const uploadImages = async (files: FileList | null) => {
    if (!files?.length) return;
    if (images.length + files.length > 5) {
      setError("사진은 최대 5장까지 등록할 수 있습니다.");
      return;
    }
    setUploading(true);
    setError("");
    try {
      const uploadedUrls: string[] = [];
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append("file", file);
        const response = await csrfFetch(API_ENDPOINTS.images.upload, {
          method: "POST",
          body: formData,
        });
        if (!response.ok) {
          const data = await response.json().catch(() => null);
          throw new Error(data?.message || "이미지 업로드에 실패했습니다.");
        }
        const uploaded: { imageUrl: string } = await response.json();
        uploadedUrls.push(uploaded.imageUrl);
      }
      setImages((current) => [...current, ...uploadedUrls]);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!detail) return;
    const content = editorRef.current?.getInstance().getMarkdown()?.trim() ?? detail.content.trim();
    const numericPrice = free ? 0 : Number(price);
    if (!categoryCode || !title.trim() || !content || !contact.trim()) {
      setError("상품 분류, 제목, 본문, 연락처를 모두 입력해주세요.");
      return;
    }
    if (!free && (!Number.isInteger(numericPrice) || numericPrice < 1 || numericPrice > 20_000_000)) {
      setError("가격은 1원 이상 2,000만원 이하로 입력해주세요.");
      return;
    }
    setSubmitting(true);
    setError("");
    const response = await csrfFetch(API_ENDPOINTS.market.update(publicId), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        version: detail.version,
        categoryCode,
        title: title.trim(),
        content,
        productCondition,
        transactionMethod,
        price: numericPrice,
        negotiable: free ? false : negotiable,
        free,
        contact: contact.trim(),
        imageUrls: images,
      }),
    });
    if (response.ok) {
      router.replace(`/market/${publicId}`);
      return;
    }
    const data = await response.json().catch(() => null);
    setError(data?.message || "판매글을 수정하지 못했습니다. 다른 곳에서 수정되었는지 확인해주세요.");
    setSubmitting(false);
  };

  return (
    <div className="community-page">
      <TopNav active="market" />
      <main className="community-container market-compose-page">
        <header className="market-compose-heading"><div><Link href="/market">중고장터</Link><span>/</span><strong>판매글 수정</strong></div><h1>판매 상품 정보를 수정해주세요.</h1></header>
        {!detail ? <div className="market-state">{error || "판매글을 불러오는 중입니다."}</div> : <form className="market-compose-card" onSubmit={(event) => void submit(event)}>
          {error && <div className="compose-error" role="alert">{error}</div>}
          <div className="market-form-grid">
            <label><span>상품 분류 *</span><select value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)}>{categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label>
            <label><span>상품 상태 *</span><select value={productCondition} onChange={(event) => setProductCondition(event.target.value)}>{conditions.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
          </div>
          <fieldset className="market-radio-field"><legend>거래 방식 *</legend>{methods.map(([code, label]) => <label key={code}><input type="radio" name="transactionMethod" checked={transactionMethod === code} onChange={() => setTransactionMethod(code)} />{label}</label>)}</fieldset>
          <label className="market-form-field"><span>글 제목 *</span><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} /></label>
          <div className="market-price-row"><label><span>가격 *</span><div><input type="number" min={1} max={20_000_000} value={free ? "" : price} onChange={(event) => setPrice(event.target.value)} disabled={free} /><b>원</b></div></label><label className="market-check"><input type="checkbox" checked={negotiable} onChange={(event) => setNegotiable(event.target.checked)} disabled={free} />가격 협의 가능</label><label className="market-check"><input type="checkbox" checked={free} onChange={(event) => { setFree(event.target.checked); if (event.target.checked) setNegotiable(false); }} />무료 나눔</label></div>
          <label className="market-form-field"><span>연락처 *</span><input value={contact} onChange={(event) => setContact(event.target.value)} maxLength={30} /></label>
          <section className="market-editor-field"><span>본문 *</span><div className="compose-editor"><ToastEditor ref={editorRef} initialValue={detail.content} height="440px" /></div></section>
          <section className="market-image-field">
            <div><span>사진 <small>선택, 최대 5장</small></span><b>{images.length}/5</b></div>
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={uploading || images.length >= 5} onChange={(event) => void uploadImages(event.target.files)} />
            {images.length > 0 && <div className="market-image-previews">{images.map((imageUrl, index) => <div key={`${imageUrl}-${index}`}><Image src={imageUrl} alt={`상품 사진 ${index + 1}`} width={120} height={90} unoptimized /><button type="button" onClick={() => setImages((current) => current.filter((_, itemIndex) => itemIndex !== index))} aria-label={`${index + 1}번 사진 제거`}><span className="material-symbols-outlined">close</span></button></div>)}</div>}
          </section>
          <div className="market-compose-actions"><Link href={`/market/${publicId}`}>취소</Link><button type="submit" disabled={submitting || uploading}>{submitting ? "수정 중..." : "수정 완료"}</button></div>
        </form>}
      </main>
      <Footer />
    </div>
  );
}
