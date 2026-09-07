# 멀티클라우드 견적 자동화 시스템

AWS / Azure / GCP의 **공개 가격 API**를 연동해, 워크로드 스펙(vCPU·RAM·스토리지·리전·OS)을 입력하면
벤더별 월 비용 견적을 자동 산출·비교해 주는 서비스입니다. 견적 이력은 저장해 재활용하고,
고객 제안용 PDF 견적서로 내보낼 수 있습니다.

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA, Scheduler, Validation |
| DB | H2(로컬 기본) / MySQL(Docker·RDS) |
| Frontend | React 18, TypeScript, Vite, Axios, Recharts |
| 외부 연동 | Azure Retail Prices, AWS Price List Query, GCP Cloud Billing Catalog |
| 그 외 | OpenPDF(견적서), springdoc(Swagger), Docker Compose + nginx |

## 아키텍처

```
[React] ──REST──▶ [Spring Boot] ──▶ [H2 / MySQL]
                        ▲              Vendor / PriceSnapshot
                        │              QuoteRequest / QuoteItem
              PriceSyncScheduler (매일 1회)
                        │
                        ├─(HTTP)──────────▶ Azure Retail Prices API      (인증 없음)
                        ├─(AWS SDK/SigV4)─▶ AWS Price List Query API     (IAM 자격증명)
                        └─(HTTP + API Key)▶ GCP Cloud Billing Catalog API (기본 비활성)
```

**설계 포인트** — 외부 API를 요청 경로에서 직접 호출하지 않고, 스케줄러가 주기적으로 수집해
`PriceSnapshot`에 캐싱한 뒤 내부 API가 캐시를 서빙합니다. 응답 속도를 확보하고 외부 API 장애 시에도
마지막 캐시로 서비스가 지속됩니다. 동기화는 벤더/리전/OS 조합 단위로 분리돼 한 벤더가 실패해도
나머지 캐시는 유지됩니다.

## 빠른 시작

```bash
# 백엔드 — 클라우드 계정 없이 샘플 가격으로 바로 확인
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,sample

# 프론트엔드
cd frontend && npm install && npm run dev      # http://localhost:5173

# 실제 가격 동기화 (스케줄러는 기본 매일 03:00 KST)
curl -X POST http://localhost:8080/api/v1/prices/sync
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

- **Azure** 인증 불필요 · **AWS** `pricing:GetProducts` 권한의 IAM 자격증명 필요(조회는 무료)
- **GCP** `app.vendors.gcp.enabled=true` + `GCP_API_KEY` 설정 시 활성화

## API

Base URL `/api/v1` — 상세 스펙은 Swagger UI 참고

| Method | Path | 설명 |
|---|---|---|
| POST | `/quotes` | 견적 생성 (201) |
| GET | `/quotes/{id}` | 견적 단건 조회 |
| GET | `/quotes` | 이력 목록. `page` `size` + `region` `vendor` `os` `from` `to` 필터 |
| GET | `/quotes/{id}/pdf` | 견적서 PDF 내려받기 |
| GET | `/prices` | 캐시된 가격 조회 (관리/디버그용) |
| POST | `/prices/sync` | 가격 수동 동기화 |
| GET | `/meta` | 지원 리전/벤더/OS 목록 |

에러는 공통 포맷(`timestamp` `status` `error` `message` `path`)으로 반환합니다.
400 입력 검증·미지원 리전 / 404 견적 없음 / 502 외부 가격 API 실패.

## 견적 산출 규칙

캐시에서 **요청 스펙 이상**(vCPU·메모리 ≥ 요청)인 SKU 중 **월 비용이 가장 낮은 것**을 매칭하고,
`시간 단가 × 730시간`에 스토리지 단가(`app.storage.*` 설정값 × 요청 GB)를 더합니다.
매칭되는 SKU가 없으면 견적을 실패시키지 않고 사유(`note`)를 담아 반환합니다.

## 배포

```bash
docker compose up -d --build     # mysql + backend + frontend(nginx) 전체 스택
```

nginx가 정적 번들을 서빙하고 `/api`·`/swagger-ui`를 백엔드로 프록시하므로 배포 환경에서는
CORS 설정이 필요 없습니다. 접속 `http://<호스트>/` · Swagger `http://<호스트>/swagger-ui.html`.
AWS 자격증명은 액세스 키보다 **EC2 인스턴스 역할(IAM Role)** 을 권장합니다.

## 알아둘 점

- ⚠️ **인증이 없습니다.** 외부 공개 시 `POST /prices/sync`를 nginx에서 차단하거나 관리자 인증을 붙이세요.
- **Azure는 가격 API가 vCPU·메모리를 주지 않아** `armSkuName`을 파싱해 스펙을 유추합니다(시리즈별 비율).
  비율이 불규칙한 N·H·M 시리즈는 매칭에서 제외합니다. 실제 스펙과 대조 검증이 필요합니다.
- **스토리지 단가는 벤더별 표준 SSD 근사치**를 설정값으로 둡니다(SKU 구조가 벤더마다 크게 다름).
- **PDF 한글 폰트는 임베드하지 않습니다.** 가볍지만 한국어 폰트가 없는 뷰어에서는 깨질 수 있습니다.
- `sample` 프로파일 단가는 **데모용 근사치**입니다. 연동 테스트 시에는 함께 켜지 마세요.

설정값은 `backend/src/main/resources/application.yml` 참고 (동기화 주기·대상 리전·페이지 상한·벤더 on/off).

## 테스트

```bash
cd backend && ./mvnw test     # 서비스/REST/파싱 테스트
cd frontend && npm run build  # 타입체크 + 프로덕션 빌드
```

## 로드맵

Phase 1~4 완료 — MVP 비교 견적 · GCP 연동 · PDF 내보내기/조건 필터 · Docker Compose 배포

## 참고 자료

- [Azure Retail Prices REST API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)
- [AWS Price List Query API](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/using-price-list-query-api.html)
- [GCP Cloud Billing Catalog API](https://docs.cloud.google.com/billing/v1/how-tos/catalog-api)
