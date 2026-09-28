# 리조트캠 API 명세

## GET `/api/v1/resort-cams`

활성 리조트와 활성 카메라를 표시 순서대로 반환합니다. 인증은 요구하지 않습니다.

### 200 OK

```json
{
  "resorts": [
    {
      "code": "PHOENIX",
      "name": "휘닉스 파크",
      "regionName": "강원 평창",
      "displayOrder": 1,
      "cameras": [
        {
          "code": "PENGUIN",
          "name": "펭귄 슬로프",
          "sourceType": "HLS",
          "sourceUrl": "https://stream.example/camera.m3u8",
          "externalPageUrl": "https://official.example/webcam",
          "displayOrder": 1
        }
      ]
    }
  ]
}
```

### 응답 규칙

- 리조트: `displayOrder ASC, id ASC`
- 카메라: `displayOrder ASC, id ASC`
- 비활성 리조트와 카메라는 제외
- 활성 카메라가 없는 리조트는 이번 API에서 제외
- `resorts`와 `cameras`는 생성자에서 `List.copyOf()` 적용
- Entity를 직접 직렬화하지 않고 전용 응답 DTO 사용

### 캐시

메타데이터는 자주 변경되지 않으므로 `Cache-Control: public, max-age=300`을 적용할 수 있습니다. 세션이나 회원별 값이 포함되지 않습니다. CDN 캐시는 배포 환경의 쿠키 전달 정책을 확인한 뒤 활성화합니다.

### 오류

이 API는 목록형이므로 데이터가 없으면 404가 아니라 빈 배열을 반환합니다. DB 조회 실패만 공통 `INTERNAL_SERVER_ERROR`로 처리합니다.

## 관리 API

등록·수정·삭제·순서 변경 API는 이번 범위에 포함하지 않습니다. 추후 관리자 도메인 설계 시 별도 명세를 작성합니다.

