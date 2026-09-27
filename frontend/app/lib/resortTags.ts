export interface ResortMeta {
  code: string;
  koreanName: string;
  shortName: string;
  markerClass: string;
}

export const RESORT_MAP: Record<string, ResortMeta> = {
  PHOENIX: { code: "PHOENIX", koreanName: "휘팍", shortName: "휘팍", markerClass: "bg-[#3f6f8f]" },
  VIVALDI: { code: "VIVALDI", koreanName: "비발디", shortName: "비발", markerClass: "bg-[#555246]" },
  HIGH1: { code: "HIGH1", koreanName: "하이원", shortName: "하이", markerClass: "bg-[#6c4c99]" },
  YONGPYONG: { code: "YONGPYONG", koreanName: "용평", shortName: "용평", markerClass: "bg-[#d75a91]" },
  WELLI_HILLI: { code: "WELLI_HILLI", koreanName: "웰팍", shortName: "웰팍", markerClass: "bg-[#106962]" },
  MUJU: { code: "MUJU", koreanName: "무주", shortName: "무주", markerClass: "bg-[#2563eb]" },
  JISAN: { code: "JISAN", koreanName: "지산", shortName: "지산", markerClass: "bg-[#f2b335]" },
  KONJIAM: { code: "KONJIAM", koreanName: "곤지암", shortName: "곤지", markerClass: "resort-marker-konjiam" },
  ETC: { code: "ETC", koreanName: "기타", shortName: "기타", markerClass: "bg-[#9a3412]" },
};

export const RESORT_OPTIONS = [
  { value: "", label: "일반" },
  ...Object.values(RESORT_MAP).map((resort) => ({ value: resort.code, label: resort.koreanName })),
];
