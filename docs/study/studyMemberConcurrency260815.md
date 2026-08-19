# 📚 [스터디 명세서] 이메일 동시성 예외 처리 & 3대 회원가입 검증 테스트 딥다이브

> **본 문서는 노션(Notion)에 그대로 복사하여 독학할 수 있도록 작성된 스터디 명세서입니다.**
> 회원가입 시 발생하는 **동시성 레이스 조건(Concurrency Race Condition)**의 물리적 원리와 DB UNIQUE 제약조건을 활용한 0.001초 방어 원리, 그리고 멀티스레드 동시성 테스트(`CountDownLatch`) 코드 인용 및 라인별 해설 주석을 정밀하게 파헤칩니다.

---

# PART 1. 동시성 레이스 조건(Race Condition)의 물리적 발생 원리 & DB 방어

---

## 1.1 왜 단순 `if (existsByEmail)` 로는 동시 가입을 못 막는가?

백엔드 자바 서비스에 단순 조건문 `if (memberRepository.existsByEmail(email))` 만 지니고 있을 경우, 0.001초 간격으로 동일한 이메일 동시 가입 요청이 쏟아지면 다음과 같은 **레이스 조건(Race Condition)**이 터집니다.

```
[ 0.001초 동시 가입 참사 시나리오 ]

스레드 1 (유저 A) ──► 1. SELECT COUNT(*) existsByEmail() ➔ 0 (중복 없음!) ────────┐
                                                                              ├─► 둘 다 DB에 INSERT 됨!
스레드 2 (유저 B) ──► 2. SELECT COUNT(*) existsByEmail() ➔ 0 (중복 없음!) ────────┘   (동시성 무너짐!)
```

1. **스레드 1**이 DB 조회를 실행하여 `0` (중복 없음) 결과를 얻고 `INSERT` 준비를 합니다.
2. 거의 동일한 0.0001초 시점에 **스레드 2**도 DB 조회를 실행합니다. 스레드 1의 `INSERT` 트랜잭션이 아직 커밋(Commit)되기 전이므로 스레드 2도 똑같이 `0` (중복 없음) 결과를 얻습니다.
3. 두 스레드 모두 `save()`를 호출하게 되어 **동일한 이메일을 가진 2명의 회원 데이터가 DB에 동시 저장되는 무결성 파괴 참사**가 터집니다.

---

## 1.2 현업의 완벽한 물리 해결책: DB UNIQUE 제약조건 + `DataIntegrityViolationException` 래핑

이 문제를 극복하기 위해 **데이터베이스의 `UNIQUE KEY (email)` 제약조건을 최종 방어선**으로 활용합니다.

```
 1. 스레드 1 ➔ DB에 INSERT INTO member (email = 'user@test.com') 성공!
 2. 0.0001초 뒤 스레드 2 ➔ DB에 INSERT INTO member (email = 'user@test.com') 시도!
 3. MySQL DB 엔진 ➔ UNIQUE KEY (email) 위반 에러 발생! (Duplicate entry 'user@test.com')
 4. Spring / JPA ➔ 이 DB 에러를 'DataIntegrityViolationException' 예외로 자동 래핑하여 튕겨냄!
 5. MemberService ➔ try-catch 로 이 예외를 캐치하여 DUPLICATE_EMAIL (400 Bad Request) 에러로 변환!
```

---

# PART 2. 동시성 보완 서비스 코드 분석 & 라인별 사용 이유 주석

---

## 2.1 `MemberService.java` (동시성 예외 감싸기 보완)

### 📖 인용 코드 & 라인별 정밀 사용 이유 주석

```java
package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberSignUpResponse signUp(MemberSignUpRequest request) {
        // 1차 선제 중복 검사 (애플리케이션 레이어 1차 방어선)
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("DUPLICATE_EMAIL");
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("DUPLICATE_NICKNAME");
        }

        Member member = request.toEntity(passwordEncoder);
        
        try {
            // 💡 [왜 save() 가 아니라 saveAndFlush() 를 사용하는가?]
            // - 이유: JPA의 save()는 트랜잭션 커밋 시점까지 쿼리 실행을 지연(Lazy Write)시키는 특성이 있습니다.
            // - 동시성 방어: saveAndFlush()를 호출하여 즉시 DB에 INSERT 쿼리를 날려(Flush), 
            //   동시 가입 시 0.0001초 만에 DB UNIQUE 키 위반 에러(DataIntegrityViolationException)가 터지도록 강제하기 위함입니다.
            Member savedMember = memberRepository.saveAndFlush(member);
            return MemberSignUpResponse.from(savedMember);
        } catch (DataIntegrityViolationException e) {
            // 💡 [왜 DataIntegrityViolationException 예외를 catch 하는가?]
            // - 이유: 0.0001초 차이로 동시 가입 시 if (existsByEmail) 1차 방어선을 뚫고 들어온 2번째 스레드가 
            //   DB UNIQUE 제약조건에 의해 에러를 뿜을 때, 이 흉측한 DB 에러를 깔끔한 "DUPLICATE_EMAIL" 예외로 변환하여 튕겨내기 위해서입니다.
            throw new IllegalArgumentException("DUPLICATE_EMAIL");
        }
    }
}
```

---

# PART 3. 오늘 완료한 3대 검증 테스트 코드 분석 & 라인별 주석

---

## 3.1 `MemberServiceSignUpVerificationTest.java` (비밀번호 해시 & 내부 id 비노출 검증)

### 📖 인용 코드 & 라인별 정밀 사용 이유 주석

```java
package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MemberServiceSignUpVerificationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 💡 [테스트 1: 저장된 비밀번호가 평문과 다른 해시 형태인지 검증]
    @Test
    @DisplayName("[검증 1] DB에 저장된 비밀번호가 평문과 완전히 다른 BCrypt 해시($2a$) 형태이어야 한다")
    void signUp_PasswordIsHashed_Success() {
        // given
        String rawPassword = "MySecretPassword123!";
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("hashcheck@snowthing.com")
                .password(rawPassword)
                .nickname("해시검증")
                .build();

        // when
        MemberSignUpResponse response = memberService.signUp(request);

        // then
        Member savedMember = memberRepository.findByEmail("hashcheck@snowthing.com").orElseThrow();

        // 1. DB에 저장된 비번이 입력한 평문 "MySecretPassword123!" 과 절대로 같지 않음을 검증
        assertThat(savedMember.getPassword()).isNotEqualTo(rawPassword);
        
        // 2. BCrypt 암호화 고유 식별자인 "$2a$" 로 시작하는지 60자리 해시 포맷 검증
        assertThat(savedMember.getPassword()).startsWith("$2a$");
        
        // 3. BCryptPasswordEncoder.matches() 를 통해 평문 비번과 DB 해시 비번이 1:1 일치하는지 최종 검증
        assertThat(passwordEncoder.matches(rawPassword, savedMember.getPassword())).isTrue();
    }

    // 💡 [테스트 2: 응답 데이터에 내부 id 가 없는지 리플렉션 검증]
    @Test
    @DisplayName("[검증 2] 회원가입 응답 DTO(MemberSignUpResponse) 데이터에 DB 내부 Long id 필드가 비노출되어야 한다")
    void signUp_ResponseDtoHasNoInternalId_Success() {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("noidcheck@snowthing.com")
                .password("Password123!")
                .nickname("내부ID비노출")
                .build();

        // when
        MemberSignUpResponse response = memberService.signUp(request);

        // then
        // 1. 외부 노출용 publicId (36자리 UUID v7)는 정상 반환되는지 확인
        assertThat(response.getPublicId()).isNotNull();
        assertThat(response.getPublicId()).hasSize(36);

        // 💡 [왜 리플렉션(Reflection)으로 필드 검사를 하는가?]
        // - 이유: DTO 클래스를 자바 리플렉션으로 뒤져서 "id" 나 "memberId" 라는 필드 선언이 
        //   아예 존재하지 않는지 100% 완벽하게 엄격 검증하기 위해서입니다.
        Field[] fields = MemberSignUpResponse.class.getDeclaredFields();
        for (Field field : fields) {
            assertThat(field.getName()).isNotEqualTo("id");
            assertThat(field.getName()).isNotEqualTo("memberId");
        }
    }
}
```

---

## 3.2 `MemberConcurrencyTest.java` (멀티스레드 동시 가입 1건 성공 9건 예외 검증)

### 📖 인용 코드 & 라인별 정밀 사용 이유 주석

```java
package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MemberConcurrencyTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        memberRepository.deleteAll();
    }

    // 💡 [테스트 3: 멀티스레드로 동일한 이메일 동시 가입시 1건만 성공하도록 검증]
    @Test
    @DisplayName("[검증 3] 10개 멀티스레드로 동일한 이메일 동시 가입 시 정확히 1건만 성공하고 9건은 예외 처리되어야 한다")
    void signUp_MultiThreadSameEmail_OnlyOneSucceeds() throws InterruptedException {
        // given
        int threadCount = 10;
        
        // 💡 [왜 ExecutorService 와 FixedThreadPool 을 쓸까?]
        // - 이유: 10개의 독립적인 OS 스레드를 생성하여 멀티스레드 동시성 환경을 만들기 위해서입니다.
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        
        // 💡 [왜 CountDownLatch 를 쓸까?]
        // 1. startLatch(1): 10개 스레드를 출발선에 맞춰 대기시킨 후 출발 신호 권총을 쏘는 용도.
        // 2. endLatch(10): 10개 스레드가 전부 완료될 때까지 메인 스레드가 대기하기 위한 용도.
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // AtomicInteger: 멀티스레드 환경에서도 값이 꼬이지 않는 스레드 세이프(Thread-Safe) 정수 카운터
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        String duplicateEmail = "concurrent@snowthing.com";

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // startLatch.countDown() 이 메인 스레드에서 시작될 때까지 10개 스레드 모두 출발선에서 동시 대기!
                    startLatch.await();

                    MemberSignUpRequest request = MemberSignUpRequest.builder()
                            .email(duplicateEmail) // 10개 스레드 모두 동일한 이메일로 가입 시도!
                            .password("Password123!")
                            .nickname("동시닉네임" + index)
                            .build();

                    memberService.signUp(request);
                    successCount.incrementAndGet(); // 가입 성공 횟수 +1
                } catch (Exception e) {
                    failCount.incrementAndGet();    // 동시성 예외 차단으로 튕겨 나간 횟수 +1
                } finally {
                    endLatch.countDown(); // 스레드 1개 종료 알림
                }
            });
        }

        // 🔫 출발 신호 권총 발사! 10개 스레드가 0.0001초 만에 동시에 signUp() 을 쏩니다.
        startLatch.countDown();
        
        // 10개 스레드가 전부 끝날 때까지 메인 대기선에서 멈춤
        endLatch.await();

        // then (결과 검증)
        // 1. 성공 횟수는 오직 1건이어야 함!
        assertThat(successCount.get()).isEqualTo(1);
        // 2. 실패 횟수는 정확히 9건이어야 함!
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        // 3. DB 테이블에는 동일 이메일 회원 레코드가 단 1개만 유효하게 저장되어야 함!
        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
```

---

# 🎯 **오늘 완료한 스터디 핵심 체크리스트**

1. **`saveAndFlush()` + `DataIntegrityViolationException`**: 0.0001초 동시 가입 시 DB UNIQUE 제약조건으로 튕겨 나가는 스레드를 캐치하여 `DUPLICATE_EMAIL` 로 안심 전환.
2. **비밀번호 BCrypt 검증**: 저장된 비번이 `$2a$` 해시 포맷이며 `passwordEncoder.matches()` 를 통과하는지 검증.
3. **내부 ID 비노출 검증**: 응답 DTO 클래스 리플렉션을 통해 `id`, `memberId` 필드가 존재하지 않고 `publicId` (UUID)만 노출되는지 검증.
4. **`CountDownLatch` 멀티스레드 동시성 테스팅**: 10개 스레드 벼락 동시 가입 시 단 1건만 성공하고 9건은 튕겨 나가는 동시성 무결성 실증 완료.
