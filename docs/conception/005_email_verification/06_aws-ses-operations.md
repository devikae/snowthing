# Amazon SES 운영 설계

## 1. 리전과 발신 Identity

- 리전: `ap-northeast-2`
- 도메인 Identity: `snowthing.org`
- 발신 주소: `SnowThing <help@snowthing.org>`
- DKIM: Easy DKIM RSA 2048
- Configuration Set: `snowthing-mail-transactional`

SES Identity와 샌드박스 상태는 리전별로 관리되므로 애플리케이션과 같은 서울 리전에 구성합니다. CloudFront 인증서의 `us-east-1` 요구사항과는 관계가 없습니다.

## 2. Cloudflare DNS

SES가 제공하는 DKIM CNAME을 그대로 등록하고 모두 `DNS only`로 둡니다. 사용자 지정 MAIL FROM을 사용하면 `mail.snowthing.org`에 SES가 안내하는 MX와 SPF TXT를 추가합니다. DMARC는 `_dmarc.snowthing.org`에 모니터링 정책 `p=none`으로 시작해 반송·정렬 상태를 확인한 뒤 강화합니다.

## 3. 샌드박스 해제

SES 샌드박스에서는 검증한 수신자에게만 보낼 수 있으므로 운영 전 Production access를 요청합니다. 요청 시 트랜잭션 인증 메일만 발송하고, 수신 동의 없는 마케팅 메일을 보내지 않으며, 반송·불만을 억제 목록으로 관리한다고 설명합니다.

## 4. IAM

EC2 인스턴스 역할에 `ses:SendEmail`만 추가합니다. Resource는 서울 리전의 `snowthing.org` Identity ARN으로 제한하고 가능한 경우 `ses:FromAddress` 조건으로 `help@snowthing.org`만 허용합니다.

AWS 액세스 키와 SMTP 비밀번호는 GitHub, EC2 환경 파일과 애플리케이션 이미지에 저장하지 않습니다. AWS SDK의 기본 자격 증명 체인이 EC2 역할을 사용합니다.

## 5. 애플리케이션 설정

운영 환경 변수:

```text
SNOWTHING_MAIL_PROVIDER=ses
SNOWTHING_SES_REGION=ap-northeast-2
SNOWTHING_MAIL_FROM=help@snowthing.org
SNOWTHING_MAIL_FROM_NAME=SnowThing
SNOWTHING_SES_CONFIGURATION_SET=snowthing-mail-transactional
SNOWTHING_EMAIL_VERIFICATION_SECRET=<secret>
```

비밀값은 `/etc/snowthing/prod.env`에서 관리합니다. 발신 주소·리전·Configuration Set은 비밀값이 아니지만 배포 환경별 설정으로 둡니다.

## 6. 발송과 관측

- SES API 접수 성공 시 messageId 저장
- SDK timeout·throttling·권한·Identity 오류를 구분해 내부 로그 기록
- 사용자 응답은 상세 AWS 오류를 노출하지 않고 `EMAIL_007`로 통일
- Configuration Set에서 Send, Reject, Delivery, Bounce, Complaint, DeliveryDelay 지표 게시
- account-level suppression list에서 Bounce와 Complaint 활성화
- CloudWatch에 발송 실패율·반송률·불만률 경보 설정

`SendEmail` 성공은 SES 접수 성공이지 사용자 메일함 도착 보장이 아닙니다. 이번 범위에서는 배달 이벤트를 DB에 저장하지 않으며, 인증번호 제출 성공을 실제 수신 확인으로 사용합니다.

## 7. 로컬과 테스트

- 단위·통합 테스트는 `FakeEmailSender`를 주입해 외부 AWS를 호출하지 않습니다.
- Fake는 인증번호 원문을 운영 로그에 쓰지 않습니다.
- SES SDK 계약 테스트는 요청의 리전·발신자·수신자·Configuration Set·제목과 HTML/Text 본문 구성을 검증합니다.
- 실제 SES 발송 검증은 샌드박스의 검증된 개발자 이메일로 별도 수행합니다.
