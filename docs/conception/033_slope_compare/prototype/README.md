# 코스 맵 프로토타입

OSM 슬로프 선과 리프트 선으로 간략 맵을 만드는 실험용 코드다. 서비스 코드가 아니다. 실제 데이터 생성은 `scripts/slope-data/build_slope_data.py`가 한다.

1. Overpass API로 리조트 범위의 `piste:type=downhill`, `aerialway` 선을 받는다.

   ```
   [out:json][timeout:60];
   (way["piste:type"="downhill"](37.17,128.78,37.24,128.87);
    way["aerialway"](37.17,128.78,37.24,128.87););
   out geom tags;
   ```

2. `python gen_map.py high1.json out.html course_tpl.html` (전체 맵은 `map_tpl.html`)

- `course_tpl.html`: 슬로프별 코스 카드, 주변 슬로프 관계 판정과 라벨 배치
- `map_tpl.html`: 리조트 전체 간략 맵
- 데이터: © OpenStreetMap contributors (ODbL)
