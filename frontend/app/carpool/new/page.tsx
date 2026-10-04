"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { csrfFetch } from "../../lib/csrfFetch";
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
  fuelType: FuelType;
  fuelEfficiency: number;
  costMode: CostMode;
  fuelPrice: number;
  fuelPriceSource: FuelPriceSource;
  fuelPriceObservedAt: string;
  routeDistanceKm: number;
  routeTollFee: number;
  estimatedFuelCost: number;
  estimatedTotalCost: number;
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
  contactInfo: string | null;
  contactPublicToGuest: boolean;
  canManage: boolean;
  routeSource: RouteSource;
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
  equipmentLoadAvailable: false,
};

const HOUR_OPTIONS = Array.from({ length: 12 }, (_, index) =>
  String(index + 1),
);
const MINUTE_OPTIONS = ["00", "10", "20", "30", "40", "50"];
const PREVIEW_DEBOUNCE_MILLISECONDS = 500;
const MAX_PASSENGER_CAPACITY = 20;
const MAX_FUEL_EFFICIENCY = 100;
const MAX_MANUAL_COST_PER_PERSON = 1_000_000;
const MAX_CONTENT_LENGTH = 10_000;
const MIN_MANUAL_DISTANCE_KM = 1;
const MAX_MANUAL_DISTANCE_KM = 2_000;
const MIN_MANUAL_FUEL_PRICE = 1_000;
const MAX_MANUAL_FUEL_PRICE = 3_000;
const MAX_MANUAL_TOLL_FEE = 20_000;

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
  const [form, setForm] = useState(initial);
  const [tripType, setTripType] = useState<TripType>("ONE_WAY");
  const [fuelType, setFuelType] = useState<FuelType>("GASOLINE");
  const [costMode, setCostMode] = useState<CostMode>("AUTO");
  const [routeSource, setRouteSource] = useState<RouteSource>("KAKAO");
  const [manualFuelRequired, setManualFuelRequired] = useState(false);
  const [resorts, setResorts] = useState<Resort[]>([]);
  const [selectedPlace, setSelectedPlace] = useState<MapPlace | null>(null);
  const [calculation, setCalculation] = useState<Calculation | null>(null);
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
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
          fuelEfficiency: String(detail.fuelEfficiency),
          manualCostPerPerson:
            detail.costMode === "MANUAL"
              ? String(detail.estimatedCostPerPerson)
              : "",
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
          equipmentLoadAvailable: detail.equipmentLoadAvailable,
        });
        setTripType(detail.tripType);
        setFuelType(detail.fuelType);
        setCostMode(detail.costMode);
        setRouteSource(detail.routeSource);
        setManualFuelRequired(detail.fuelPriceSource === "USER_INPUT");
        setSelectedPlace(place);
        setInitialMapPlace(place);
        setCalculation({
          distanceKm: detail.routeDistanceKm,
          tollFee: detail.routeTollFee,
          durationSeconds: 0,
          fuelPrice: detail.fuelPrice,
          fuelPriceTradeDate: detail.fuelPriceObservedAt.slice(0, 10),
          fuelPriceSource: detail.fuelPriceSource,
          estimatedFuelCost: detail.estimatedFuelCost,
          estimatedTotalCost: detail.estimatedTotalCost,
          estimatedCostPerPerson: detail.estimatedCostPerPerson,
          totalPassengerCount: detail.passengerCapacity + 1,
        });
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
        "manualCostPerPerson",
        "manualDistanceKm",
        "manualTollFee",
        "manualFuelPrice",
      ].includes(key)
    )
      setCalculation(null);
  };

  const handleMapError = useCallback((message: string) => {
    setError(message);
    if (message.includes("불러오지 못했습니다") || message.includes("키가 설정되지")) {
      setRouteSource("MANUAL");
      setCalculation(null);
    }
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
        setError(
          previewError instanceof Error
            ? previewError.message
            : "자동 계산에 실패했습니다.",
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
    if (routeSource === "KAKAO" && selectedPlace && form.destinationResortId) {
      const timer = window.setTimeout(
        () => void preview(),
        PREVIEW_DEBOUNCE_MILLISECONDS,
      );
      return () => window.clearTimeout(timer);
    }
  }, [form.destinationResortId, preview, routeSource, selectedPlace]);

  useEffect(
    () => () => previewAbortController.current?.abort(),
    [],
  );

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if ((routeSource === "KAKAO" && !selectedPlace) || !calculation) {
      setError("출발 장소를 선택하고 예상 비용을 먼저 계산해 주세요.");
      return;
    }
    setWorking(true);
    setError("");
    try {
      const response = await csrfFetch(
        editPublicId
          ? API_ENDPOINTS.carpool.update(editPublicId)
          : API_ENDPOINTS.carpool.create,
        {
        method: editPublicId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          destinationResortId: Number(form.destinationResortId),
          passengerCapacity: Number(form.passengerCapacity),
          fuelEfficiency: Number(form.fuelEfficiency),
          costMode,
          routeSource,
          manualDistanceKm:
            routeSource === "MANUAL" ? Number(form.manualDistanceKm) : null,
          manualTollFee:
            routeSource === "MANUAL" ? Number(form.manualTollFee) : null,
          manualFuelPrice: manualFuelRequired
            ? Number(form.manualFuelPrice)
            : null,
          manualCostPerPerson:
            costMode === "MANUAL" ? Number(form.manualCostPerPerson) : null,
          tripType,
          fuelType,
          departureLatitude:
            routeSource === "KAKAO" ? selectedPlace?.latitude : null,
          departureLongitude:
            routeSource === "KAKAO" ? selectedPlace?.longitude : null,
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
        }),
      });
      if (!response.ok) {
        const apiError = await readApiError(
          response,
          response.status === 401
            ? "로그인이 필요합니다."
            : "모집글 등록에 실패했습니다.",
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
      const data = await response.json();
      router.push(`/carpool/${data.publicId}`);
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "등록에 실패했습니다.",
      );
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <TopNav active="carpool" />
      <main className="community-container carpool-compose-page">
        <header className="carpool-compose-heading">
          <Link href="/carpool">카풀·동행</Link>
          <h1>카풀 모집글 {editPublicId ? "수정" : "등록"}</h1>
          <p>
            출발지와 리조트를 선택하면 현재 경로와 전국 평균 유가로 예상 비용을
            계산합니다.
          </p>
        </header>
        {!authChecked ? <div className="carpool-auth-gate">로그인 상태를 확인하는 중입니다.</div> : !authenticated ? (
          <section className="carpool-auth-gate">
            <span className="material-symbols-outlined">lock</span>
            <h2>카풀 모집글은 회원만 작성할 수 있습니다.</h2>
            <p>회원가입 후 출발지와 예상 비용을 계산해 모집글을 등록해보세요.</p>
            <div><Link className="carpool-primary-button" href="/signup">회원가입</Link><Link href="/login?returnUrl=/carpool/new">이미 회원이라면 로그인</Link></div>
          </section>
        ) : <form className="carpool-form" onSubmit={submit}>
          <section>
            <h2>제목</h2>
            <label>
              <input
                required
                value={form.title}
                onChange={(event) => update("title", event.target.value)}
                placeholder="카풀 모집글 제목"
              />
            </label>
          </section>
          <section>
            <h2>출발 장소</h2>
            <KakaoMapPicker
              onSelect={handlePlaceSelect}
              onError={handleMapError}
              initialPlace={initialMapPlace}
            />
            {routeSource === "MANUAL" && (
              <div className="carpool-manual-fallback" role="status">
                <strong>카카오 지도·길찾기 장애 대응 직접 입력</strong>
                <p>
                  출발 장소와 선택한 이동 방식 기준의 총 이동 거리·통행료를
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
              <label>
                이동 방식
                <select
                  value={tripType}
                  onChange={(event) => {
                    setTripType(event.target.value as TripType);
                    setCalculation(null);
                  }}
                >
                  <option value="ONE_WAY">편도</option>
                  <option value="ROUND_TRIP">왕복</option>
                </select>
              </label>
              <label>
                출발 날짜
                <input
                  required
                  type="date"
                  value={form.departureDate}
                  onChange={(event) =>
                    update("departureDate", event.target.value)
                  }
                />
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
                    <input
                      required
                      type="date"
                      value={form.returnDate}
                      onChange={(event) =>
                        update("returnDate", event.target.value)
                      }
                    />
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
              <label className="carpool-check carpool-equipment-check">
                <input
                  type="checkbox"
                  checked={form.equipmentLoadAvailable}
                  onChange={(event) => update("equipmentLoadAvailable", event.target.checked)}
                />
                스키·보드 장비 적재 가능
              </label>
            </div>
          </section>
          <section>
            <h2>차량·비용</h2>
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
              <label>
                비용 방식
                <select
                  value={costMode}
                  onChange={(event) => {
                    setCostMode(event.target.value as CostMode);
                    setCalculation(null);
                  }}
                >
                  <option value="AUTO">경로·유가 자동 계산</option>
                  <option value="MANUAL">1인 금액 직접 입력</option>
                </select>
              </label>
              {costMode === "MANUAL" && (
                <label>
                  1인 부담액
                  <input
                    required
                    min="1"
                    max={MAX_MANUAL_COST_PER_PERSON}
                    step="100"
                    type="number"
                    value={form.manualCostPerPerson}
                    onChange={(event) =>
                      update("manualCostPerPerson", event.target.value)
                    }
                    placeholder="예: 15000"
                  />
                </label>
              )}
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
                  : "경로·유가 자동 계산"}
            </button>
            {calculation && (
              <div className="carpool-preview-result">
                <div>
                  <span>총 거리</span>
                  <b>{calculation.distanceKm.toLocaleString("ko-KR")}km</b>
                </div>
                <div>
                  <span>통행료</span>
                  <b>{calculation.tollFee.toLocaleString("ko-KR")}원</b>
                </div>
                <div>
                  <span>
                    {calculation.fuelPriceSource === "USER_INPUT"
                      ? "사용자 입력 유가"
                      : "전국 평균 유가"}
                  </span>
                  <b>{calculation.fuelPrice.toLocaleString("ko-KR")}원/L</b>
                  <small>
                    {calculation.fuelPriceSource === "CACHE"
                      ? "유효 캐시 사용"
                      : calculation.fuelPriceTradeDate
                        ? `${calculation.fuelPriceTradeDate} 기준`
                        : "직접 입력값"}
                  </small>
                </div>
                <div>
                  <span>예상 연료비</span>
                  <b>
                    {calculation.estimatedFuelCost.toLocaleString("ko-KR")}원
                  </b>
                </div>
                <div>
                  <span>예상 총액</span>
                  <b>
                    {calculation.estimatedTotalCost.toLocaleString("ko-KR")}원
                  </b>
                </div>
                <div>
                  <span>{calculation.totalPassengerCount}명 기준 1인</span>
                  <strong>
                    {(costMode === "MANUAL"
                      ? Number(form.manualCostPerPerson)
                      : calculation.estimatedCostPerPerson
                    ).toLocaleString("ko-KR")}
                    원
                  </strong>
                </div>
              </div>
            )}
          </section>
          <section>
            <h2>모집 내용</h2>
            <textarea
              required
              maxLength={MAX_CONTENT_LENGTH}
              placeholder="카풀 일정과 장비 적재 가능 여부를 적어주세요."
              value={form.content}
              onChange={(event) => update("content", event.target.value)}
            />
          </section>
          <section>
            <h2>연락처 공개</h2>
            <label className="carpool-contact-field">
              연락처(선택)
              <input
                value={form.contactInfo}
                onChange={(event) => update("contactInfo", event.target.value)}
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
              비회원에게 연락처 공개
            </label>
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
              disabled={working || !calculation}
            >
              모집글 {editPublicId ? "수정" : "등록"}
            </button>
          </footer>
        </form>}
      </main>
      <Footer />
    </>
  );
}
