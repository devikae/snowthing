# DTO Skill

## 불변성 보장

DTO 생성 시 `List`, `Set`, `Map` 등의 컬렉션을 인자로 받을 경우 외부 가변성이 DTO 내부로 전파되지 않도록 방어적 복사를 수행한다.

예:

```java
this.items = List.copyOf(items);
```

또는 필요 시:

```java
this.items = new ArrayList<>(items);
```

## 원칙

- 외부에서 원본 컬렉션이 변경되어 DTO 상태가 같이 바뀌는 것을 막는다.
- DTO 자체의 불변성을 의도했다면 내부 컬렉션도 같은 수준으로 보호한다.
- 단순 관습이 아니라 Mutability 오염을 막기 위한 규칙으로 적용한다.
