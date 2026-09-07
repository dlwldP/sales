# 멀티클라우드 견적 자동화 시스템

AWS / Azure / GCP의 **공개 가격 API**를 연동해, 워크로드 스펙(vCPU·RAM·스토리지·리전·OS)을 입력하면
벤더별 월 비용 견적을 자동 산출·비교해 주는 서비스입니다.

- 영업/기획 담당자가 각 클라우드 콘솔 가격표를 수동으로 뒤지지 않고 즉시 비교 견적을 얻습니다.
- 하드코딩된 가격표가 아니라 벤더 공개 API 기반이라 가격 변동에 강합니다.
- 견적 이력을 저장해 고객사별 제안 자료로 재활용할 수 있습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA, Spring Scheduler, Spring Validation |
| DB | H2(로컬 기본) / MySQL(Docker·RDS) |
| Frontend | React 18, TypeScript, Vite, Axios, Recharts |
| 외부 연동 | Azure Retail Prices API, AWS Price List Query API, GCP Cloud Billing Catalog API(Phase 2) |
| 문서화 | Swagger / OpenAPI (springdoc) |

## 아키텍처

```
[React Client]
     │ (REST, JSON)
     ▼
[Spring Boot API Server]
     ├─ QuoteController      → 견적 생성/조회/이력
     ├─ PriceController      → 캐시된 가격 조회, 수동 동기화
     ├─ MetaController       → 폼 구성용 리전/벤더/OS 목록
     ├─ PriceSyncScheduler   → 매일 1회 외부 API 폴링 → PriceSnapshot 저장
     ▼
[H2 / MySQL] ── Vendor / PriceSnapshot / QuoteRequest / QuoteItem

PriceSyncScheduler ──(HTTP)──────────▶ Azure Retail Prices API      (인증 없음)
                   ──(AWS SDK/SigV4)─▶ AWS Price List Query API     (IAM 자격증명)
                   ──(HTTP + API Key)▶ GCP Cloud Billing Catalog API (Phase 2)
```

**설계 포인트** — 외부 API를 요청 경로에서 직접 호출하지 않고, 스케줄러가 주기적으로 수집해
DB(`PriceSnapshot`)에 캐싱한 뒤 내부 API가 캐시를 서빙합니다.

- Azure/GCP는 페이지당 최대 1,000건 응답이라 매 요청마다 풀스캔하면 느립니다.
- AWS는 IAM 서명 호출 자체에 레이턴시가 있습니다.
- 프론트 응답속도를 확보하고, 외부 API 장애 시에도 마지막 캐시로 서비스가 지속됩니다.

한 벤더의 동기화가 실패해도 나머지 벤더 캐시는 그대로 유지되도록 벤더/리전/OS 조합 단위로
트랜잭션과 예외 처리를 분리했습니다.

## 빠른 시작

### 1. 백엔드 (샘플 데이터 포함, 자격증명 불필요)

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,sample
# 또는: mvn spring-boot:run -Dspring-boot.run.profiles=local,sample
```

- `local` 프로파일: 인메모리 H2 사용 (`http://localhost:8080/h2-console`)
- `sample` 프로파일: `sample-prices.sql`의 **샘플 가격**을 적재해 클라우드 계정 없이 UI/로직 확인
  > 샘플 단가는 데모용 근사치이며 실제 청구 단가가 아닙니다. 운영에서는 스케줄러가 채운 데이터를 사용합니다.
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 2. 프론트엔드

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173 (→ /api 요청은 8080으로 프록시)
```

### 3. MySQL로 실행 (Docker)

```bash
docker compose up -d mysql
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 4. 실제 가격 동기화

```bash
# 스케줄러(기본 매일 03:00 KST)를 기다리지 않고 즉시 동기화
curl -X POST http://localhost:8080/api/v1/prices/sync
```

- **Azure**: 인증 불필요. 바로 동작합니다.
- **AWS**: `pricing:GetProducts` 권한이 있는 IAM 자격증명이 필요합니다.
  `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 환경변수 또는 `~/.aws/credentials` 프로파일을 사용합니다.
  (조회 API 호출 자체는 무료입니다.)
- **GCP (Phase 2)**: `app.vendors.gcp.enabled=true` + `GCP_API_KEY` 환경변수 설정 시 활성화됩니다.

## API 명세

Base URL: `/api/v1`

| Method | Path | 설명 |
|---|---|---|
| POST | `/quotes` | 견적 생성 (201) |
| GET | `/quotes/{quoteId}` | 견적 단건 조회 |
| GET | `/quotes?page=0&size=20` | 견적 이력 목록 (생성일 내림차순) |
| GET | `/prices?vendor=AWS&region=korea&vcpu=4&memoryGb=16` | 캐시된 가격 조회 (관리/디버그용) |
| POST | `/prices/sync` | 가격 수동 동기화 |
| GET | `/meta` | 지원 리전/벤더/OS 목록 |

### 견적 생성 예시

```bash
curl -X POST http://localhost:8080/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{
    "workloadName": "테스트 서버",
    "vcpu": 4, "memoryGb": 16, "storageGb": 100,
    "region": "korea", "os": "LINUX",
    "vendors": ["AWS", "AZURE"]
  }'
```

```json
{
  "quoteId": 1,
  "workloadName": "테스트 서버",
  "vcpu": 4, "memoryGb": 16, "storageGb": 100,
  "region": "korea", "os": "LINUX",
  "createdAt": "2026-09-07T14:00:00+09:00",
  "results": [
    { "vendor": "AWS",   "matchedSku": "t3.xlarge",       "computeCostUsd": 151.84, "storageCostUsd": 9.12, "monthlyCostUsd": 160.96 },
    { "vendor": "AZURE", "matchedSku": "Standard_D4s_v5", "computeCostUsd": 163.52, "storageCostUsd": 8.80, "monthlyCostUsd": 172.32 }
  ]
}
```

### 에러 응답 (공통 포맷)

```json
{
  "timestamp": "2026-09-07T14:00:00+09:00",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "vcpu는 1 이상이어야 합니다.",
  "path": "/api/v1/quotes"
}
```

| 상태코드 | 상황 |
|---|---|
| 400 | 입력값 검증 실패, 미지원 리전 |
| 404 | 견적 ID 없음 |
| 502 | 외부 가격 API 응답 실패 |

## 견적 산출 규칙

1. 요청 리전(논리 키 `korea` 등)을 벤더 리전 코드(`ap-northeast-2`, `koreacentral`)로 변환합니다.
2. 캐시에서 **요청 스펙 이상**(vCPU ≥ 요청, 메모리 ≥ 요청)인 SKU 중 **월 비용이 가장 낮은 것**을 매칭합니다.
3. 월 비용 = 시간 단가 × **730시간**(24h × 365d ÷ 12개월).
4. 스토리지 비용 = `app.storage.rate-usd-per-gb-month` 단가 × 요청 GB를 가산합니다.
   - 스토리지 SKU 구조가 벤더마다 크게 달라 MVP에서는 벤더별 표준 SSD 단가를 설정값으로 둡니다.
5. 매칭되는 SKU가 없으면 금액 대신 사유(`note`)를 담아 반환합니다(견적 자체는 실패하지 않음).

### 벤더별 스펙 해석 노트

- **AWS**: `GetProducts` 응답의 `product.attributes`에 vCPU·메모리가 있어 그대로 사용합니다.
  `terms.OnDemand → priceDimensions → pricePerUnit.USD` 중 단위가 `Hrs`인 값을 단가로 씁니다.
- **Azure**: Retail Prices 응답에 vCPU·메모리가 **없어** `armSkuName`(`Standard_D4s_v5`)을 파싱해
  시리즈별 vCPU당 메모리 비율(D=4GB, E=8GB, F=2GB, L=8GB, B=2/4GB)로 스펙을 유추합니다.
  비율이 일정하지 않은 GPU(N)·HPC(H)·초대형 메모리(M) 시리즈는 매칭 대상에서 제외합니다.
- **GCP (Phase 2)**: 머신 타입 단가가 아니라 Core/Ram SKU가 분리되어 있어,
  패밀리별 코어·메모리 단가를 수집해 표준 형태(`n2-standard-4` 등) 단가를 합성합니다.

## 주요 설정 (`backend/src/main/resources/application.yml`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.price-sync.cron` | `0 0 3 * * *` | 동기화 스케줄 |
| `app.price-sync.zone` | `Asia/Seoul` | 스케줄 타임존 |
| `app.price-sync.regions` | `[korea]` | 동기화 대상 논리 리전 |
| `app.price-sync.run-on-startup` | `false` | 기동 시 1회 동기화 |
| `app.price-sync.max-pages` | `20` | 벤더 API 페이지네이션 상한 |
| `app.vendors.{aws,azure,gcp}.enabled` | `true/true/false` | 벤더 연동 on/off |
| `app.storage.rate-usd-per-gb-month` | AWS 0.0912 / AZURE 0.088 / GCP 0.085 | 스토리지 단가 |

지원 리전(논리 키): `korea`, `tokyo`, `singapore`, `us-east`, `west-europe`

## 테스트

```bash
cd backend && mvn test        # 서비스/REST/파싱 단위·통합 테스트
cd frontend && npm run build  # 타입체크 + 프로덕션 빌드
```

## 프로젝트 구조

```
backend/
  src/main/java/com/multicloud/quote/
    config/      AppProperties, RegionCatalog, AwsPricingConfig, WebConfig, OpenApiConfig
    controller/  QuoteController, PriceController, MetaController
    dto/         요청/응답 DTO
    entity/      Vendor, PriceSnapshot, QuoteRequest, QuoteItem
    exception/   GlobalExceptionHandler, ErrorResponse, 도메인 예외
    repository/  Spring Data JPA 리포지토리
    scheduler/   PriceSyncScheduler
    service/     QuoteService, PriceService, PriceSyncService, PriceCacheWriter, CostCalculator
      vendor/    AwsPriceClient, AzurePriceClient, GcpPriceClient, AzureSkuSpecResolver
frontend/
  src/
    api/         Axios 클라이언트, 에러 메시지 정규화
    components/  QuoteForm, ComparisonTable, ComparisonChart, QuoteHistory
    types/       API 타입 정의
```

## 로드맵

| Phase | 범위 | 상태 |
|---|---|---|
| Phase 1 (MVP) | AWS+Azure 견적 비교, 스케줄러 캐싱, React 비교 테이블/차트 | 완료 |
| Phase 2 | GCP 연동, 견적 이력 페이지네이션 | 구현 완료(GCP는 API Key 설정 시 활성화) |
| Phase 3 | 견적서 PDF 내보내기, 조건별(리전/기간) 필터 고도화 | 예정 |
| Phase 4 | AWS EC2 배포, Swagger 문서 공개 | 예정 |

## 참고 자료

- [Azure Retail Prices REST API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)
- [AWS Price List Query API 가이드](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/using-price-list-query-api.html)
- [AWS Pricing GetProducts API Reference](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_pricing_GetProducts.html)
- [GCP Cloud Billing Catalog API](https://docs.cloud.google.com/billing/v1/how-tos/catalog-api)
