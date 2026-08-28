# Exception Handling Skill

## 문자열 리터럴 기반 예외 처리 금지

다음과 같은 패턴을 사용하지 않는다.

```java
throw new IllegalArgumentException("INVALID_CREDENTIALS");
```

```java
if ("DUPLICATE_EMAIL".equals(e.getMessage())) {
    ...
}
```

## 원칙

예외는 `ErrorCode` Enum과 정적 타입 기반의 비즈니스 커스텀 예외(`CustomException`)로 일원화한다.

예:

```java
throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
```

문자열 메시지를 프로그램 분기 조건으로 사용하지 않는다.

## 이유

- 문자열 오타를 컴파일 타임에 검증할 수 없다.
- 메시지 변경이 비즈니스 분기를 깨뜨릴 수 있다.
- 에러 코드와 사용자 메시지의 책임이 뒤섞인다.
- 전역 예외 처리와 API 응답 표준화가 어려워진다.
