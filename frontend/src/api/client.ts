import axios, { AxiosError } from 'axios';
import type {
  ApiError,
  Meta,
  PageResponse,
  Quote,
  QuoteCreateRequest,
  QuoteSummary,
} from '../types/quote';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

/** 서버 공통 에러 포맷(message)을 사용자에게 그대로 보여주기 위해 정규화한다. */
export function toErrorMessage(error: unknown): string {
  const axiosError = error as AxiosError<ApiError>;
  if (axiosError?.response?.data?.message) {
    return axiosError.response.data.message;
  }
  if (axiosError?.code === 'ECONNABORTED') {
    return '요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (axiosError?.message) {
    return `요청에 실패했습니다: ${axiosError.message}`;
  }
  return '알 수 없는 오류가 발생했습니다.';
}

export async function createQuote(request: QuoteCreateRequest): Promise<Quote> {
  const { data } = await api.post<Quote>('/quotes', request);
  return data;
}

export async function fetchQuote(quoteId: number): Promise<Quote> {
  const { data } = await api.get<Quote>(`/quotes/${quoteId}`);
  return data;
}

export async function fetchQuotes(page = 0, size = 20): Promise<PageResponse<QuoteSummary>> {
  const { data } = await api.get<PageResponse<QuoteSummary>>('/quotes', { params: { page, size } });
  return data;
}

export async function fetchMeta(): Promise<Meta> {
  const { data } = await api.get<Meta>('/meta');
  return data;
}

export default api;
