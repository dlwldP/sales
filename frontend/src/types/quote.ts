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

export interface QuoteItem {
  vendor: VendorType;
  matchedSku: string | null;
  computeCostUsd: number | null;
  storageCostUsd: number | null;
  monthlyCostUsd: number | null;
  note?: string | null;
}

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
