export type VendorType = 'AWS' | 'AZURE' | 'GCP';
export type OsType = 'LINUX' | 'WINDOWS';

export interface QuoteCreateRequest {
  workloadName: string;
  vcpu: number;
  memoryGb: number;
  storageGb: number;
  region: string;
  os: OsType;
  vendors: VendorType[];
}

/**
 * 서버가 null 필드를 응답에서 생략하므로(Jackson non_null) 값이 없을 때는 undefined로 도착한다.
 * 가격 유무를 판단할 때는 `!== null`이 아니라 `hasPrice()`를 쓸 것.
 */
export interface QuoteItem {
  vendor: VendorType;
  matchedSku?: string | null;
  computeCostUsd?: number | null;
  storageCostUsd?: number | null;
  monthlyCostUsd?: number | null;
  note?: string | null;
}

/** 매칭 SKU가 없어 금액이 산출되지 않은 항목을 걸러낸다. */
export const hasPrice = (item: QuoteItem): item is QuoteItem & { monthlyCostUsd: number } =>
  typeof item.monthlyCostUsd === 'number';

export interface Quote {
  quoteId: number;
  workloadName: string;
  vcpu: number;
  memoryGb: number;
  storageGb: number;
  region: string;
  os: OsType;
  createdAt: string;
  results: QuoteItem[];
}

export interface QuoteSummary {
  quoteId: number;
  workloadName: string;
  vcpu: number;
  memoryGb: number;
  storageGb: number;
  region: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** 견적 이력 조건 필터. 빈 문자열은 "조건 없음"으로 취급한다. */
export interface QuoteHistoryFilter {
  region: string;
  vendor: string;
  from: string;
  to: string;
}

export const EMPTY_FILTER: QuoteHistoryFilter = { region: '', vendor: '', from: '', to: '' };

export interface Meta {
  regions: string[];
  vendors: VendorType[];
  osTypes: OsType[];
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
