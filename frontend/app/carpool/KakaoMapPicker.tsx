"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type MapPlace = {
  name: string;
  addressName: string;
  roadAddressName: string;
  longitude: number;
  latitude: number;
};

type KakaoMapPickerProps = {
  onSelect: (place: MapPlace) => void;
  onError: (message: string) => void;
  initialPlace?: MapPlace | null;
};

const KAKAO_SDK_URL = "https://dapi.kakao.com/v2/maps/sdk.js";
const KAKAO_SDK_SCRIPT_ID = "kakao-map-sdk";
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 };
const SEARCH_PAGE_SIZE = 10;

declare global {
  interface Window {
    kakao?: KakaoNamespace;
  }
}

type KakaoNamespace = {
  maps: {
    load: (callback: () => void) => void;
    Map: new (element: HTMLElement, options: KakaoMapOptions) => KakaoMap;
    LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
    Marker: new (options: {
      map: KakaoMap;
      position: KakaoLatLng;
    }) => KakaoMarker;
    event: {
      addListener: (
        target: KakaoMap,
        event: string,
        handler: (mouseEvent: KakaoMouseEvent) => void,
      ) => void;
    };
    services: {
      Places: new () => KakaoPlaces;
      Geocoder: new () => KakaoGeocoder;
      Status: { OK: string };
    };
  };
};

type KakaoMapOptions = { center: KakaoLatLng; level: number };
type KakaoLatLng = { getLat: () => number; getLng: () => number };
type KakaoMap = {
  panTo: (center: KakaoLatLng) => void;
};
type KakaoMarker = { setPosition: (position: KakaoLatLng) => void };
type KakaoMouseEvent = { latLng: KakaoLatLng };
type KakaoPlaceResult = {
  place_name: string;
  address_name: string;
  road_address_name: string;
  x: string;
  y: string;
};
type KakaoPlaces = {
  keywordSearch: (
    query: string,
    callback: (results: KakaoPlaceResult[], status: string) => void,
    options?: { size: number },
  ) => void;
};
type KakaoGeocoder = {
  coord2Address: (
    longitude: number,
    latitude: number,
    callback: (results: KakaoAddressResult[], status: string) => void,
  ) => void;
};
type KakaoAddressResult = {
  address?: { address_name: string };
  road_address?: { address_name: string };
};

export default function KakaoMapPicker({
  onSelect,
  onError,
  initialPlace,
}: KakaoMapPickerProps) {
  const mapElement = useRef<HTMLDivElement>(null);
  const map = useRef<KakaoMap | null>(null);
  const marker = useRef<KakaoMarker | null>(null);
  const places = useRef<KakaoPlaces | null>(null);
  const geocoder = useRef<KakaoGeocoder | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<KakaoPlaceResult[]>([]);
  const [ready, setReady] = useState(false);
  const [message, setMessage] = useState("");

  const reportError = useCallback(
    (value: string) => {
      setMessage(value);
      onError(value);
    },
    [onError],
  );

  const selectCoordinate = useCallback((coordinate: KakaoLatLng, moveMap: boolean) => {
    if (!map.current || !marker.current || !geocoder.current || !window.kakao)
      return;
    marker.current.setPosition(coordinate);
    if (moveMap) map.current.panTo(coordinate);
    geocoder.current.coord2Address(
      coordinate.getLng(),
      coordinate.getLat(),
      (items, status) => {
        if (status !== window.kakao?.maps.services.Status.OK || items.length === 0) {
          reportError("선택한 위치의 주소를 확인하지 못했습니다.");
          return;
        }
        const item = items[0];
        const selectedPlace = {
          name: item.road_address?.address_name || item.address?.address_name || "선택한 위치",
          addressName: item.address?.address_name || item.road_address?.address_name || "",
          roadAddressName: item.road_address?.address_name || "",
          longitude: coordinate.getLng(),
          latitude: coordinate.getLat(),
        };
        setQuery(selectedPlace.roadAddressName || selectedPlace.addressName || selectedPlace.name);
        onSelect(selectedPlace);
      },
    );
  }, [onSelect, reportError]);

  useEffect(() => {
    const initialize = () => {
      if (!window.kakao?.maps || !mapElement.current) return;
      window.kakao.maps.load(() => {
        if (!window.kakao || !mapElement.current) return;
        const center = new window.kakao.maps.LatLng(
          initialPlace?.latitude ?? DEFAULT_CENTER.latitude,
          initialPlace?.longitude ?? DEFAULT_CENTER.longitude,
        );
        map.current = new window.kakao.maps.Map(mapElement.current, {
          center,
          level: 5,
        });
        marker.current = new window.kakao.maps.Marker({
          map: map.current,
          position: center,
        });
        places.current = new window.kakao.maps.services.Places();
        geocoder.current = new window.kakao.maps.services.Geocoder();
        if (initialPlace) {
          setQuery(
            initialPlace.roadAddressName ||
              initialPlace.addressName ||
              initialPlace.name,
          );
        }
        window.kakao.maps.event.addListener(map.current, "click", (event) =>
          selectCoordinate(event.latLng, false),
        );
        setReady(true);
      });
    };

    const existing = document.getElementById(KAKAO_SDK_SCRIPT_ID);
    if (existing) {
      initialize();
      return;
    }
    const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_APP_KEY;
    if (!appKey) {
      const timer = window.setTimeout(() => reportError("카카오 지도 JavaScript 키가 설정되지 않았습니다."), 0);
      return () => window.clearTimeout(timer);
    }
    const script = document.createElement("script");
    script.id = KAKAO_SDK_SCRIPT_ID;
    script.async = true;
    script.src = `${KAKAO_SDK_URL}?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=services`;
    script.onload = initialize;
    script.onerror = () =>
      reportError(
        "카카오 지도를 불러오지 못했습니다. Web 플랫폼 도메인을 확인해 주세요.",
      );
    document.head.appendChild(script);
  }, [initialPlace, reportError, selectCoordinate]);

  const search = () => {
    if (!query.trim() || !places.current || !window.kakao) return;
    places.current.keywordSearch(
      query.trim(),
      (items, status) => {
        if (
          status !== window.kakao?.maps.services.Status.OK ||
          items.length === 0
        ) {
          setResults([]);
          reportError(
            "검색 결과가 없습니다. 주소나 장소명을 더 구체적으로 입력해 주세요.",
          );
          return;
        }
        setResults(items.slice(0, SEARCH_PAGE_SIZE));
        reportError("");
      },
      { size: SEARCH_PAGE_SIZE },
    );
  };

  const choose = (item: KakaoPlaceResult) => {
    if (!window.kakao) return;
    const coordinate = new window.kakao.maps.LatLng(
      Number(item.y),
      Number(item.x),
    );
    setResults([]);
    selectCoordinate(coordinate, true);
  };

  return (
    <div className="kakao-map-picker">
      <div className="carpool-place-search">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              search();
            }
          }}
          placeholder="주소 또는 장소명 검색"
        />
        <button type="button" onClick={search} disabled={!ready}>
          검색
        </button>
      </div>
      <div ref={mapElement} className="kakao-map" aria-label="출발 장소 지도" />
      {results.length > 0 && (
        <div className="carpool-place-results">
          {results.map((item) => (
            <button
              type="button"
              key={`${item.x}-${item.y}`}
              onClick={() => choose(item)}
            >
              <strong>{item.place_name}</strong>
              <span>{item.road_address_name || item.address_name}</span>
            </button>
          ))}
        </div>
      )}
      {message && (
        <p className="kakao-map-message" role="alert">
          {message}
        </p>
      )}
      <p className="kakao-map-hint">
        검색 결과를 선택하거나 지도를 직접 눌러 출발 장소를 지정하세요.
      </p>
    </div>
  );
}
