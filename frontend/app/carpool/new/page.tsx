"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { PageHeading } from "../../components/PageHeading";
import { csrfFetch } from "../../lib/csrfFetch";
import { useLeaveWarning } from "../../lib/useLeaveWarning";
import { IDEMPOTENCY_HEADER, useIdempotencyKey } from "../../lib/idempotencyKey";
import { API_ENDPOINTS } from "../../lib/api";
import KakaoMapPicker, { MapPlace } from "../KakaoMapPicker";

type TripType = "ONE_WAY" | "ROUND_TRIP";
type FuelType = "GASOLINE" | "DIESEL" | "LPG" | "HYBRID_GASOLINE";
type CostMode = "AUTO" | "MANUAL";
type RouteSource = "KAKAO" | "MANUAL";
type FuelPriceSource = "OPINET" | "CACHE" | "USER_INPUT";
type Resort = { id: number; name: string; regionName: string };
type Calculation = {
  distanceKm: number;
  tollFee: number;
  durationSeconds: number;
  fuelPrice: number;
  fuelPriceTradeDate: string | null;
  fuelPriceSource: FuelPriceSource;
  estimatedFuelCost: number;
  estimatedTotalCost: number;
  estimatedCostPerPerson: number;
  totalPassengerCount: number;
};

type CarpoolEditDetail = {
  title: string;
  content: string;
  departureRegion: string;
  meetingPlace: string;
  departureLatitude: number | null;
  departureLongitude: number | null;
  destinationResortId: number;
  tripType: TripType;
  departureAt: string;
  returnAt: string | null;
  passengerCapacity: number;
  fuelType: FuelType | null;
  fuelEfficiency: number | null;
  costMode: CostMode;
  fuelPrice: number | null;
  fuelPriceSource: FuelPriceSource | null;
  fuelPriceObservedAt: string | null;
  routeDistanceKm: number | null;
  routeTollFee: number | null;
  estimatedFuelCost: number | null;
  estimatedTotalCost: number | null;
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
  contactInfo: string | null;
  contactPublicToGuest: boolean;
  canManage: boolean;
  routeSource: RouteSource | null;
};

type ApiErrorResponse = { code?: string; message?: string };

async function readApiError(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as ApiErrorResponse;
    return { code: body.code, message: body.message?.trim() || fallback };
  } catch {
    return { code: undefined, message: fallback };
  }
}

const initial = {
  title: "",
  content: "",
  departureRegion: "",
  meetingPlace: "",
  destinationResortId: "",
  departureDate: "",
  departurePeriod: "AM",
  departureHour: "",
  departureMinute: "",
  returnDate: "",
  returnPeriod: "AM",
  returnHour: "",
  returnMinute: "",
  passengerCapacity: "1",
  fuelEfficiency: "12",
  manualCostPerPerson: "",
  manualDistanceKm: "",
  manualTollFee: "0",
  manualFuelPrice: "",
  contactInfo: "",
  contactPublicToGuest: false,
};

const HOUR_OPTIONS = Array.from({ length: 12 }, (_, index) =>
  String(index + 1),
);
const MINUTE_OPTIONS = ["00", "10", "20", "30", "40", "50"];
const PREVIEW_DEBOUNCE_MILLISECONDS = 500;

function getDayOfWeek(dateString: string): string {
  if (!dateString) return "";
  const days = ["일", "월", "화", "수", "목", "금", "토"];
  const d = new Date(`${dateString}T00:00:00`);
  if (isNaN(d.getTime())) return "";
  return `(${days[d.getDay()]}요일)`;
}

const MAX_PASSENGER_CAPACITY = 20;
const MAX_FUEL_EFFICIENCY = 100;
const MAX_MANUAL_COST_PER_PERSON = 1_000_000;
const MAX_CONTENT_LENGTH = 10_000;
const MIN_MANUAL_DISTANCE_KM = 1;
const MAX_MANUAL_DISTANCE_KM = 2_000;
const MIN_MANUAL_FUEL_PRICE = 1_000;
const MAX_MANUAL_FUEL_PRICE = 3_000;
const MAX_MANUAL_TOLL_FEE = 20_000;

const KST_DATE_FORMAT = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul" });

const toIsoDateTime = (
  date: string,
  period: string,
  hour: string,
  minute: string,
) => {
  if (!date || !hour || !minute) return null;
  const parsedHour = (Number(hour) % 12) + (period === "PM" ? 12 : 0);
  return `${date}T${String(parsedHour).padStart(2, "0")}:${minute}:00`;
};

const splitLocalDateTime = (value: string | null) => {
  if (!value) return { date: "", period: "AM", hour: "", minute: "" };
  const [date, time = ""] = value.split("T");
  const [hourText = "0", minute = "00"] = time.split(":");
  const hour24 = Number(hourText);
  return {
    date,
    period: hour24 >= 12 ? "PM" : "AM",
    hour: String(hour24 % 12 || 12),
    minute,
  };
};

export default function NewCarpoolPage() {
  const router = useRouter();
  useLeaveWarning();
  const [form, setForm] = useState(initial);
  const [tripType, setTripType] = useState<TripType>("ONE_WAY");
  const [fuelType, setFuelType] = useState<FuelType>("GASOLINE");
  const [costMode, setCostMode] = useState<CostMode>("AUTO");
  const [routeSource, setRouteSource] = useState<RouteSource>("KAKAO");
  const [manualFuelRequired, setManualFuelRequired] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);
  // 새 글은 값을 미리 정하지 않는다. 체크를 안 했다고 "적재 불가"로 올라가는 일을 막는다.
  const [equipmentLoadAvailable, setEquipmentLoadAvailable] = useState<boolean | null>(null);
  const [resorts, setResorts] = useState<Resort[]>([]);
  const [selectedPlace, setSelectedPlace] = useState<MapPlace | null>(null);
  const [calculation, setCalculation] = useState<Calculation | null>(null);
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  const isSubmittingRef = useRef(false);
  const idempotencyKey = useIdempotencyKey();
  const todayIsoDate = KST_DATE_FORMAT.format(new Date());
  const [authChecked, setAuthChecked] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [editPublicId, setEditPublicId] = useState<string | null>(null);
  const [initialMapPlace, setInitialMapPlace] = useState<MapPlace | null>(null);
  const previewAbortController = useRef<AbortController | null>(null);
  const previewRequestId = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(
      () =>
        setEditPublicId(
          new URLSearchParams(window.location.search).get("edit"),
        ),
      0,
    );
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    void fetch(API_ENDPOINTS.members.me, { credentials: "include" })
      .then((response) => setAuthenticated(response.ok))
      .catch(() => setAuthenticated(false))
      .finally(() => setAuthChecked(true));
  }, []);

  useEffect(() => {
    if (!editPublicId) return;
    void fetch(API_ENDPOINTS.carpool.detail(editPublicId), {
      credentials: "include",
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(
            (await readApiError(response, "수정할 모집글을 불러오지 못했습니다."))
              .message,
          );
        }
        return response.json() as Promise<CarpoolEditDetail>;
      })
      .then((detail) => {
        if (!detail.canManage) {
          throw new Error("이 모집글을 수정할 권한이 없습니다.");
        }
        const departure = splitLocalDateTime(detail.departureAt);
        const returning = splitLocalDateTime(detail.returnAt);
        const auto = detail.costMode === "AUTO";
        const place: MapPlace | null =
          detail.departureLongitude !== null && detail.departureLatitude !== null
            ? {
                name: detail.meetingPlace,
                addressName: detail.departureRegion,
                roadAddressName: detail.meetingPlace,
                longitude: detail.departureLongitude,
                latitude: detail.departureLatitude,
              }
            : null;
        setForm({
          title: detail.title,
          content: detail.content,
          departureRegion: detail.departureRegion,
          meetingPlace: detail.meetingPlace,
          destinationResortId: String(detail.destinationResortId),
          departureDate: departure.date,
          departurePeriod: departure.period,
          departureHour: departure.hour,
          departureMinute: departure.minute,
          returnDate: returning.date,
          returnPeriod: returning.period,
          returnHour: returning.hour,
          returnMinute: returning.minute,
          passengerCapacity: String(detail.passengerCapacity),
          fuelEfficiency: String(detail.fuelEfficiency ?? initial.fuelEfficiency),
          manualCostPerPerson: auto ? "" : String(detail.estimatedCostPerPerson),
          manualDistanceKm:
            detail.routeSource === "MANUAL"
              ? String(detail.routeDistanceKm)
              : "",
          manualTollFee:
            detail.routeSource === "MANUAL" ? String(detail.routeTollFee) : "0",
          manualFuelPrice:
            detail.fuelPriceSource === "USER_INPUT"
              ? String(detail.fuelPrice)
              : "",
          contactInfo: detail.contactInfo ?? "",
          contactPublicToGuest: detail.contactPublicToGuest,
        });
        setEquipmentLoadAvailable(detail.equipmentLoadAvailable);
        setTripType(detail.tripType);
        setFuelType(detail.fuelType ?? "GASOLINE");
        setCostMode(detail.costMode);
        setRouteSource(detail.routeSource ?? "KAKAO");
        setManualFuelRequired(detail.fuelPriceSource === "USER_INPUT");
        setSelectedPlace(place);
        setInitialMapPlace(place);
        setCalculation(
          auto
            ? {
                distanceKm: detail.routeDistanceKm ?? 0,
                tollFee: detail.routeTollFee ?? 0,
                durationSeconds: 0,
                fuelPrice: detail.fuelPrice ?? 0,
                fuelPriceTradeDate: detail.fuelPriceObservedAt?.slice(0, 10) ?? null,
                fuelPriceSource: detail.fuelPriceSource ?? "OPINET",
                estimatedFuelCost: detail.estimatedFuelCost ?? 0,
                estimatedTotalCost: detail.estimatedTotalCost ?? 0,
                estimatedCostPerPerson: detail.estimatedCostPerPerson,
                totalPassengerCount: detail.passengerCapacity + 1,
              }
            : null,
        );
      })
      .catch((loadError) =>
        setError(
          loadError instanceof Error
            ? loadError.message
            : "수정할 모집글을 불러오지 못했습니다.",
        ),
      );
  }, [editPublicId]);

  useEffect(() => {
    void fetch(API_ENDPOINTS.master.resorts)
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: Resort[]) => setResorts(data))
      .catch(() => setError("리조트 목록을 불러오지 못했습니다."));
  }, []);

  const update = (key: keyof typeof initial, value: string | boolean) => {
    setForm((current) => ({ ...current, [key]: value }));
    if (
      [
        "destinationResortId",
        "passengerCapacity",
        "fuelEfficiency",
        "manualDistanceKm",
        "manualTollFee",
        "manualFuelPrice",
      ].includes(key)
    )
      setCalculation(null);
  };

  const changeCostMode = (nextMode: CostMode) => {
    if (nextMode === costMode) return;
    previewAbortController.current?.abort();
    if (nextMode === "AUTO") {
      // 경로는 지도 좌표로 계산하므로 직접 입력에서 고친 글자가 좌표와 어긋나지 않게 되돌린다.
      if (selectedPlace) {
        setForm((current) => ({
          ...current,
          departureRegion: selectedPlace.addressName,
          meetingPlace: selectedPlace.name,
        }));
      }
    }
    setCostMode(nextMode);
    setWorking(false);
    setError("");
  };

  const handleMapError = useCallback((message: string) => {
    setError(message);
    if (message.includes("불러오지 못했습니다") || message.includes("키가 설정되지")) {
      setRouteSource("MANUAL");
      setCalculation(null);
    }
  }, []);
  // 현재 위치는 동 단위 지역만 채운다. 이미 적었거나 장소를 고른 뒤라면 덮어쓰지 않는다.
  const handleLocate = useCallback((region: string) => {
    setForm((current) =>
      current.departureRegion.trim() ? current : { ...current, departureRegion: region },
    );
  }, []);
  const handlePlaceSelect = useCallback((place: MapPlace) => {
    setSelectedPlace(place);
    setForm((current) => ({
      ...current,
      departureRegion: place.addressName,
      meetingPlace: place.name,
    }));
    setCalculation(null);
    setError("");
  }, []);

  const preview = useCallback(async () => {
    if (
      !form.destinationResortId ||
      (routeSource === "KAKAO" && !selectedPlace) ||
      (routeSource === "MANUAL" &&
        (!form.departureRegion.trim() ||
          !form.meetingPlace.trim() ||
          !form.manualDistanceKm ||
          form.manualTollFee === "")) ||
      (manualFuelRequired && !form.manualFuelPrice)
    ) {
      setError("출발 장소와 도착 리조트를 먼저 선택해 주세요.");
      return;
    }
    previewAbortController.current?.abort();
    const abortController = new AbortController();
    previewAbortController.current = abortController;
    const requestId = ++previewRequestId.current;
    setWorking(true);
    setPreviewFailed(false);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.carpool.autoPreview, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          originLongitude:
            routeSource === "KAKAO" ? selectedPlace?.longitude : null,
          originLatitude:
            routeSource === "KAKAO" ? selectedPlace?.latitude : null,
          destinationResortId: Number(form.destinationResortId),
          tripType,
          fuelType,
          fuelEfficiency: Number(form.fuelEfficiency),
          passengerCapacity: Number(form.passengerCapacity),
          routeSource,
          manualDistanceKm:
            routeSource === "MANUAL" ? Number(form.manualDistanceKm) : null,
          manualTollFee:
            routeSource === "MANUAL" ? Number(form.manualTollFee) : null,
          manualFuelPrice: manualFuelRequired
            ? Number(form.manualFuelPrice)
            : null,
        }),
        signal: abortController.signal,
      });
      if (!response.ok) {
        const apiError = await readApiError(
          response,
          "경로와 유가를 자동 계산하지 못했습니다.",
        );
        if (["CARPOOL_004", "CARPOOL_006"].includes(apiError.code ?? "")) {
          setRouteSource("MANUAL");
          setCalculation(null);
          throw new Error(
            "현재 카카오 지도 또는 길찾기를 사용할 수 없습니다. 출발지와 총 이동 거리, 통행료를 직접 입력해 주세요.",
          );
        }
        if (["CARPOOL_005", "CARPOOL_007", "CARPOOL_009"].includes(apiError.code ?? "")) {
          setManualFuelRequired(true);
          setCalculation(null);
          throw new Error(
            "현재 오피넷 유가를 조회할 수 없습니다. 리터당 유가를 직접 입력해 주세요.",
          );
        }
        throw new Error(apiError.message);
      }
      const nextCalculation = await response.json();
      if (requestId === previewRequestId.current) {
        setCalculation(nextCalculation);
        setManualFuelRequired(
          nextCalculation.fuelPriceSource === "USER_INPUT",
        );
      }
    } catch (previewError) {
      if (!(previewError instanceof DOMException && previewError.name === "AbortError")) {
        setPreviewFailed(true);
        setError(
          previewError instanceof Error
            ? previewError.message
            : "비용을 자동으로 계산하지 못했습니다. 금액을 직접 입력하거나 잠시 후 다시 시도해 주세요.",
        );
      }
    } finally {
      if (requestId === previewRequestId.current) {
        setWorking(false);
      }
    }
  }, [
    form.destinationResortId,
    form.departureRegion,
    form.fuelEfficiency,
    form.manualDistanceKm,
    form.manualFuelPrice,
    form.manualTollFee,
    form.meetingPlace,
    form.passengerCapacity,
    fuelType,
    manualFuelRequired,
    routeSource,
    selectedPlace,
    tripType,
  ]);

  useEffect(() => {
    if (costMode === "AUTO" && routeSource === "KAKAO" && selectedPlace && form.destinationResortId) {
      const timer = window.setTimeout(
        () => void preview(),
        PREVIEW_DEBOUNCE_MILLISECONDS,
      );
      return () => window.clearTimeout(timer);
    }
  }, [costMode, form.destinationResortId, preview, routeSource, selectedPlace]);

  useEffect(
    () => () => previewAbortController.current?.abort(),
    [],
  );

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (isSubmittingRef.current || working) return;
    const auto = costMode === "AUTO";

    if (!form.title.trim()) {
      setError("모집글 제목을 입력해 주세요.");
      return;
    }
    if (auto && routeSource === "KAKAO" && !selectedPlace) {
      setError("출발 장소를 지도에서 검색하거나 [현재 위치] 버튼으로 지정해 주세요.");
      return;
    }
    if (!auto && (!form.departureRegion.trim() || !form.meetingPlace.trim())) {
      setError("출발 지역과 집결 장소를 입력해 주세요.");
      return;
    }
    if (!form.destinationResortId) {
      setError("도착 리조트를 선택해 주세요.");
      return;
    }
    if (!form.departureDate) {
      setError("출발 날짜를 선택해 주세요.");
      return;
    }
    if (!form.departureHour || !form.departureMinute) {
      setError("출발 시간을 선택해 주세요.");
      return;
    }
    if (tripType === "ROUND_TRIP" && (!form.returnDate || !form.returnHour || !form.returnMinute)) {
      setError("왕복인 경우 복귀 날짜와 시간을 입력해 주세요.");
      return;
    }
    if (equipmentLoadAvailable === null) {
      setError("스키, 보드 장비 적재 가능 여부를 선택해 주세요.");
      return;
    }
    if (auto && !calculation) {
      setError("예상 비용 계산이 끝난 뒤 등록해 주세요. 출발 장소와 도착 리조트를 확인해 주세요.");
      return;
    }

    isSubmittingRef.current = true;
    setWorking(true);
    setError("");
    try {
      const body = JSON.stringify({
          ...form,
          destinationResortId: Number(form.destinationResortId),
          passengerCapacity: Number(form.passengerCapacity),
          equipmentLoadAvailable,
          costMode,
          manualCostPerPerson: auto ? null : Number(form.manualCostPerPerson),
          fuelEfficiency: auto ? Number(form.fuelEfficiency) : null,
          fuelType: auto ? fuelType : null,
          routeSource: auto ? routeSource : null,
          manualDistanceKm:
            auto && routeSource === "MANUAL" ? Number(form.manualDistanceKm) : null,
          manualTollFee:
            auto && routeSource === "MANUAL" ? Number(form.manualTollFee) : null,
          manualFuelPrice:
            auto && manualFuelRequired ? Number(form.manualFuelPrice) : null,
          tripType,
          // 직접 입력도 지도에서 고른 장소가 있으면 좌표를 함께 저장한다.
          departureLatitude:
            !auto || routeSource === "KAKAO" ? selectedPlace?.latitude ?? null : null,
          departureLongitude:
            !auto || routeSource === "KAKAO" ? selectedPlace?.longitude ?? null : null,
          departureAt: toIsoDateTime(
            form.departureDate,
            form.departurePeriod,
            form.departureHour,
            form.departureMinute,
          ),
          returnAt:
            tripType === "ROUND_TRIP"
              ? toIsoDateTime(
                  form.returnDate,
                  form.returnPeriod,
                  form.returnHour,
                  form.returnMinute,
                )
              : null,
      });
      // 새 모집글만 멱등 키를 보낸다. 수정(PUT)은 대상이 정해져 있어 다시 보내도 결과가 같다.
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (!editPublicId) headers[IDEMPOTENCY_HEADER] = idempotencyKey.keyFor(body);
      const response = await csrfFetch(
        editPublicId
          ? API_ENDPOINTS.carpool.update(editPublicId)
          : API_ENDPOINTS.carpool.create,
        { method: editPublicId ? "PUT" : "POST", headers, body },
      );
      if (!response.ok) {
        const apiError = await readApiError(
          response,
          response.status === 401
            ? "로그인이 필요합니다."
            : "모집글을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        );
        if (["CARPOOL_004", "CARPOOL_006"].includes(apiError.code ?? "")) {
          setRouteSource("MANUAL");
          setCalculation(null);
        }
        if (["CARPOOL_005", "CARPOOL_007", "CARPOOL_009"].includes(apiError.code ?? "")) {
          setManualFuelRequired(true);
          setCalculation(null);
        }
        throw new Error(apiError.message);
      }
      idempotencyKey.reset();
      const data = await response.json();
      router.push(`/carpool/${data.publicId}`);
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "모집글을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      isSubmittingRef.current = false;
      setWorking(false);
    }
  };

  return (
    <>
      <div className="community-page"><TopNav active="carpool" />
      <main className="community-container carpool-compose-page content-compose-page">
        <PageHeading title={`카풀 모집글 ${editPublicId ? "수정" : "등록"}`}
          breadcrumb={<Link href="/carpool">카풀과 동행</Link>}
          description="일정과 출발 장소를 정하고 함께할 라이더를 모집해 보세요." />
        {!authChecked ? <div className="carpool-auth-gate">로그인 상태를 확인하는 중입니다.</div> : !authenticated ? (
          <section className="carpool-auth-gate">
            <span className="material-symbols-outlined" aria-hidden="true">lock</span>
            <h2>카풀 모집글은 회원만 작성할 수 있습니다.</h2>
            <p>회원가입 후 출발지와 예상 비용을 계산해 모집글을 등록해 보세요.</p>
            <div><Link className="carpool-primary-button" href="/signup">회원가입</Link><Link href="/login?returnUrl=/carpool/new">이미 회원이라면 로그인</Link></div>
          </section>
        ) : <form className="carpool-form" onSubmit={submit}>
          <section>
            <h2>제목</h2>
            <label>
              <input
                required
                maxLength={200}
                value={form.title}
                onChange={(event) => update("title", event.target.value)}
                placeholder="카풀 모집글 제목"
              />
            </label>
          </section>
          <section>
            <h2>비용 방식</h2>
            <div className="carpool-mode-toggle" role="group" aria-label="비용 방식">
              <button type="button" aria-pressed={costMode === "AUTO"} onClick={() => changeCostMode("AUTO")}>자동 계산</button>
              <button type="button" aria-pressed={costMode === "MANUAL"} onClick={() => changeCostMode("MANUAL")}>직접 입력</button>
            </div>
            <small>
              {costMode === "AUTO"
                ? "출발지와 차량 정보로 거리, 통행료, 유가를 계산합니다."
                : "받을 금액을 직접 정합니다. 경로와 유가는 계산하지 않습니다."}
            </small>
          </section>
          <section>
            <h2>출발 장소</h2>
            <KakaoMapPicker
              onSelect={handlePlaceSelect}
              onError={handleMapError}
              initialPlace={initialMapPlace}
              autoLocate={!editPublicId}
              onLocate={handleLocate}
            />
            {costMode === "MANUAL" && (
              <div className="carpool-form-grid">
                <label>
                  출발 지역
                  <input
                    required
                    maxLength={100}
                    value={form.departureRegion}
                    onChange={(event) => update("departureRegion", event.target.value)}
                    placeholder="예: 서울 강동구"
                  />
                </label>
                <label>
                  집결 장소
                  <input
                    required
                    maxLength={200}
                    value={form.meetingPlace}
                    onChange={(event) => update("meetingPlace", event.target.value)}
                    placeholder="예: 천호역 1번 출구"
                  />
                </label>
              </div>
            )}
            {costMode === "AUTO" && routeSource === "MANUAL" && (
              <div className="carpool-manual-fallback" role="status">
                <strong>카카오 지도와 길찾기 장애 대응 직접 입력</strong>
                <p>
                  출발 장소와 선택한 이동 방식 기준의 총 이동 거리, 통행료를
                  입력해 주세요.
                </p>
                <div className="carpool-form-grid">
                  <label>
                    출발 지역
                    <input
                      required
                      maxLength={100}
                      value={form.departureRegion}
                      onChange={(event) =>
                        update("departureRegion", event.target.value)
                      }
                      placeholder="예: 서울 강동구"
                    />
                  </label>
                  <label>
                    집결 장소
                    <input
                      required
                      maxLength={200}
                      value={form.meetingPlace}
                      onChange={(event) =>
                        update("meetingPlace", event.target.value)
                      }
                      placeholder="예: 천호역 1번 출구"
                    />
                  </label>
                  <label>
                    총 이동 거리(km)
                    <input
                      required
                      type="number"
                      min={MIN_MANUAL_DISTANCE_KM}
                      max={MAX_MANUAL_DISTANCE_KM}
                      step="0.01"
                      value={form.manualDistanceKm}
                      onChange={(event) =>
                        update("manualDistanceKm", event.target.value)
                      }
                      placeholder="왕복이면 왕복 총거리"
                    />
                  </label>
                  <label>
                    총 통행료(원)
                    <input
                      required
                      type="number"
                      min="0"
                      max={MAX_MANUAL_TOLL_FEE}
                      step="100"
                      value={form.manualTollFee}
                      onChange={(event) =>
                        update("manualTollFee", event.target.value)
                      }
                    />
                  </label>
                </div>
              </div>
            )}
          </section>
          <section>
            <h2>이동 정보</h2>
            <div className="carpool-form-grid">
              <label>
                도착 리조트
                <select
                  required
                  value={form.destinationResortId}
                  onChange={(event) =>
                    update("destinationResortId", event.target.value)
                  }
                >
                  <option value="">리조트 선택</option>
                  {resorts.map((resort) => (
                    <option key={resort.id} value={resort.id}>
                      {resort.name} · {resort.regionName}
                    </option>
                  ))}
                </select>
              </label>
              <div className="carpool-choice-field">
                <span>이동 방식</span>
                <div className="carpool-choice" role="group" aria-label="이동 방식">
                  {(["ONE_WAY", "ROUND_TRIP"] as const).map((value) => (
                    <button
                      key={value}
                      type="button"
                      aria-pressed={tripType === value}
                      onClick={() => {
                        setTripType(value);
                        setCalculation(null);
                      }}
                    >
                      {value === "ONE_WAY" ? "편도" : "왕복"}
                    </button>
                  ))}
                </div>
              </div>
              <label>
                출발 날짜
                <div className="carpool-date-input-container">
                  <input
                    type="text"
                    readOnly
                    tabIndex={-1}
                    className="carpool-date-text-display"
                    placeholder="날짜 선택"
                    value={form.departureDate ? `${form.departureDate}${getDayOfWeek(form.departureDate)}` : ""}
                  />
                  <span className="material-symbols-outlined carpool-date-icon" aria-hidden="true">calendar_month</span>
                  <input
                    required
                    type="date"
                    min={todayIsoDate}
                    value={form.departureDate}
                    onChange={(event) =>
                      update("departureDate", event.target.value)
                    }
                    onClick={(event) => {
                      try {
                        (event.currentTarget as HTMLInputElement).showPicker();
                      } catch {}
                    }}
                    className="carpool-native-date-input"
                  />
                </div>
              </label>
              <label>
                출발 시간
                <div className="carpool-time-selects">
                  <select
                    required
                    value={form.departurePeriod}
                    onChange={(event) =>
                      update("departurePeriod", event.target.value)
                    }
                  >
                    <option value="AM">오전</option>
                    <option value="PM">오후</option>
                  </select>
                  <select
                    required
                    value={form.departureHour}
                    onChange={(event) =>
                      update("departureHour", event.target.value)
                    }
                  >
                    <option value="">시</option>
                    {HOUR_OPTIONS.map((hour) => (
                      <option key={hour} value={hour}>
                        {hour}
                      </option>
                    ))}
                  </select>
                  <select
                    required
                    value={form.departureMinute}
                    onChange={(event) =>
                      update("departureMinute", event.target.value)
                    }
                  >
                    <option value="">분</option>
                    {MINUTE_OPTIONS.map((minute) => (
                      <option key={minute} value={minute}>
                        {minute}
                      </option>
                    ))}
                  </select>
                </div>
              </label>
              {tripType === "ROUND_TRIP" && (
                <>
                  <label>
                    복귀 날짜
                    <div className="carpool-date-input-container">
                      <input
                        type="text"
                        readOnly
                        tabIndex={-1}
                        className="carpool-date-text-display"
                        placeholder="날짜 선택"
                        value={form.returnDate ? `${form.returnDate}${getDayOfWeek(form.returnDate)}` : ""}
                      />
                      <span className="material-symbols-outlined carpool-date-icon" aria-hidden="true">calendar_month</span>
                      <input
                        required
                        type="date"
                        min={form.departureDate || todayIsoDate}
                        value={form.returnDate}
                        onChange={(event) =>
                          update("returnDate", event.target.value)
                        }
                        onClick={(event) => {
                          try {
                            (event.currentTarget as HTMLInputElement).showPicker();
                          } catch {}
                        }}
                        className="carpool-native-date-input"
                      />
                    </div>
                  </label>
                  <label>
                    복귀 시간
                    <div className="carpool-time-selects">
                      <select
                        required
                        value={form.returnPeriod}
                        onChange={(event) =>
                          update("returnPeriod", event.target.value)
                        }
                      >
                        <option value="AM">오전</option>
                        <option value="PM">오후</option>
                      </select>
                      <select
                        required
                        value={form.returnHour}
                        onChange={(event) =>
                          update("returnHour", event.target.value)
                        }
                      >
                        <option value="">시</option>
                        {HOUR_OPTIONS.map((hour) => (
                          <option key={hour} value={hour}>
                            {hour}
                          </option>
                        ))}
                      </select>
                      <select
                        required
                        value={form.returnMinute}
                        onChange={(event) =>
                          update("returnMinute", event.target.value)
                        }
                      >
                        <option value="">분</option>
                        {MINUTE_OPTIONS.map((minute) => (
                          <option key={minute} value={minute}>
                            {minute}
                          </option>
                        ))}
                      </select>
                    </div>
                  </label>
                </>
              )}
              <label>
                모집 인원
                <input
                  required
                  min="1"
                  max={MAX_PASSENGER_CAPACITY}
                  type="number"
                  value={form.passengerCapacity}
                  onChange={(event) =>
                    update("passengerCapacity", event.target.value)
                  }
                />
              </label>
              <div className="carpool-choice-field">
                <span>스키, 보드 장비 적재</span>
                <div className="carpool-choice" role="group" aria-label="스키, 보드 장비 적재">
                  <button type="button" aria-pressed={equipmentLoadAvailable === true} onClick={() => setEquipmentLoadAvailable(true)}>가능</button>
                  <button type="button" aria-pressed={equipmentLoadAvailable === false} onClick={() => setEquipmentLoadAvailable(false)}>불가</button>
                </div>
              </div>
            </div>
          </section>
          {costMode === "MANUAL" ? (
          <section>
            <h2>비용</h2>
            <label className="carpool-contact-field">
              1인 금액(원)
              <input
                required
                min="1"
                max={MAX_MANUAL_COST_PER_PERSON}
                step="1"
                type="number"
                value={form.manualCostPerPerson}
                onChange={(event) => update("manualCostPerPerson", event.target.value)}
                placeholder="예: 10000"
              />
            </label>
          </section>
          ) : (
          <section>
            <h2>차량과 비용</h2>
            <div className="carpool-form-grid">
              <label>
                유종
                <select
                  value={fuelType}
                  onChange={(event) => {
                    setFuelType(event.target.value as FuelType);
                    setCalculation(null);
                  }}
                >
                  <option value="GASOLINE">휘발유</option>
                  <option value="DIESEL">경유</option>
                  <option value="LPG">LPG</option>
                  <option value="HYBRID_GASOLINE">휘발유 하이브리드</option>
                </select>
              </label>
              <label>
                실연비(km/L)
                <input
                  required
                  min="0.01"
                  max={MAX_FUEL_EFFICIENCY}
                  step="0.01"
                  type="number"
                  value={form.fuelEfficiency}
                  onChange={(event) =>
                    update("fuelEfficiency", event.target.value)
                  }
                />
              </label>
              {manualFuelRequired && (
                <label>
                  유가 직접 입력(원/L)
                  <input
                    required
                    min={MIN_MANUAL_FUEL_PRICE}
                    max={MAX_MANUAL_FUEL_PRICE}
                    step="0.01"
                    type="number"
                    value={form.manualFuelPrice}
                    onChange={(event) =>
                      update("manualFuelPrice", event.target.value)
                    }
                    placeholder="1,000~3,000"
                  />
                </label>
              )}
            </div>
            {(routeSource === "MANUAL" || manualFuelRequired || previewFailed) && (
              <button
                className="carpool-cost-button"
                type="button"
                onClick={() => void preview()}
                disabled={working}
              >
                {working
                  ? "계산 중..."
                  : routeSource === "MANUAL" || manualFuelRequired
                    ? "입력값으로 예상 비용 계산"
                    : "다시 계산"}
              </button>
            )}
            {calculation ? (
              <dl className="carpool-preview-rows">
                <div>
                  <dt>이동 거리</dt>
                  <dd>{calculation.distanceKm.toLocaleString("ko-KR")}km</dd>
                </div>
                <div>
                  <dt>통행료</dt>
                  <dd>{calculation.tollFee.toLocaleString("ko-KR")}원</dd>
                </div>
                <div>
                  <dt>
                    {calculation.fuelPriceSource === "USER_INPUT"
                      ? "사용자 입력 유가"
                      : calculation.fuelPriceSource === "CACHE"
                        ? "전국 평균 유가(유효 캐시)"
                        : `전국 평균 유가${calculation.fuelPriceTradeDate ? `(${calculation.fuelPriceTradeDate} 기준)` : ""}`}
                  </dt>
                  <dd>{calculation.fuelPrice.toLocaleString("ko-KR")}원/L</dd>
                </div>
                <div>
                  <dt>연료비</dt>
                  <dd>{calculation.estimatedFuelCost.toLocaleString("ko-KR")}원</dd>
                </div>
                <div>
                  <dt>예상 총액</dt>
                  <dd>{calculation.estimatedTotalCost.toLocaleString("ko-KR")}원</dd>
                </div>
                <div className="total">
                  <dt>예상 1인 비용(운전자 포함 {calculation.totalPassengerCount}명 기준)</dt>
                  <dd>{calculation.estimatedCostPerPerson.toLocaleString("ko-KR")}원</dd>
                </div>
              </dl>
            ) : (
              working && <small>예상 비용을 계산하는 중입니다.</small>
            )}
          </section>
          )}
          <section>
            <h2>모집 내용</h2>
            <textarea
              required
              maxLength={MAX_CONTENT_LENGTH}
              placeholder="카풀 일정과 장비 적재 가능 여부를 적어 주세요."
              value={form.content}
              onChange={(event) => update("content", event.target.value)}
            />
          </section>
          <section>
            <h2>연락처</h2>
            <label className="carpool-contact-field">
              연락처(선택)
              <input
                maxLength={500}
                value={form.contactInfo}
                onChange={(event) => update("contactInfo", event.target.value)}
                placeholder="예: 오픈채팅 링크"
              />
            </label>
            <label className="carpool-check">
              <input
                type="checkbox"
                checked={form.contactPublicToGuest}
                onChange={(event) =>
                  update("contactPublicToGuest", event.target.checked)
                }
              />{" "}
              연락처 공개
            </label>
            <small>
              공개하면 로그인하지 않은 방문자도 볼 수 있습니다. 공개하지 않으면 작성자와
              관리자만 볼 수 있습니다. 전화번호처럼 민감한 정보는 주의해 주세요.
            </small>
          </section>
          {error && (
            <p className="carpool-form-error" role="alert">
              {error}
            </p>
          )}
          <footer>
            <Link href="/carpool">취소</Link>
            <button
              className="carpool-primary-button"
              type="submit"
              disabled={working}
            >
              {working ? "등록 처리 중..." : `모집글 ${editPublicId ? "수정" : "등록"}`}
            </button>
          </footer>
        </form>}
      </main>
      <Footer /></div>
    </>
  );
}
