import { useCallback, useEffect, useState } from 'react';
import ComparisonChart from './components/ComparisonChart';
import ComparisonTable from './components/ComparisonTable';
import QuoteForm from './components/QuoteForm';
import QuoteHistory from './components/QuoteHistory';
import { createQuote, fetchMeta, fetchQuote, fetchQuotes, toErrorMessage } from './api/client';
import type {
  Meta,
  PageResponse,
  Quote,
  QuoteCreateRequest,
  QuoteHistoryFilter,
  QuoteSummary,
} from './types/quote';
import { EMPTY_FILTER } from './types/quote';

const PAGE_SIZE = 10;

export default function App() {
  const [meta, setMeta] = useState<Meta | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [history, setHistory] = useState<PageResponse<QuoteSummary> | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [filter, setFilter] = useState<QuoteHistoryFilter>(EMPTY_FILTER);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadHistory = useCallback(async (page: number, historyFilter: QuoteHistoryFilter) => {
    try {
      setHistory(await fetchQuotes(page, PAGE_SIZE, historyFilter));
    } catch (e) {
      setError(toErrorMessage(e));
    }
  }, []);

  const handleFilterChange = (next: QuoteHistoryFilter) => {
    setFilter(next);
    // 필터가 바뀌면 결과 집합이 달라지므로 첫 페이지부터 다시 본다.
    setHistoryPage(0);
  };

  useEffect(() => {
    fetchMeta().then(setMeta).catch((e) => setError(toErrorMessage(e)));
  }, []);

  useEffect(() => {
    void loadHistory(historyPage, filter);
  }, [historyPage, filter, loadHistory]);

  const handleSubmit = async (request: QuoteCreateRequest) => {
    setSubmitting(true);
    setError(null);
    try {
      const created = await createQuote(request);
      setQuote(created);
      // 새 견적이 목록 맨 앞에 오도록 첫 페이지를 다시 읽는다.
      setHistoryPage(0);
      await loadHistory(0, filter);
    } catch (e) {
      setError(toErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSelect = async (quoteId: number) => {
    setError(null);
    try {
      setQuote(await fetchQuote(quoteId));
    } catch (e) {
      setError(toErrorMessage(e));
    }
  };

  return (
    <div className="app">
      <header>
        <h1>멀티클라우드 견적 자동화</h1>
        <p>워크로드 스펙을 입력하면 AWS / Azure 공개 가격 기준 월 비용을 비교해 드립니다.</p>
      </header>

      {error && (
        <div className="alert" role="alert">
          {error}
        </div>
      )}

      <main>
        <section className="left">
          <QuoteForm meta={meta} submitting={submitting} onSubmit={handleSubmit} />
          <QuoteHistory
            page={history}
            meta={meta}
            filter={filter}
            selectedId={quote?.quoteId ?? null}
            onFilterChange={handleFilterChange}
            onSelect={handleSelect}
            onPageChange={setHistoryPage}
          />
        </section>

        <section className="right">
          {quote ? (
            <>
              <ComparisonTable quote={quote} />
              <ComparisonChart quote={quote} />
            </>
          ) : (
            <div className="card empty">
              <p className="muted">
                왼쪽에서 스펙을 입력하고 <strong>견적 산출</strong>을 누르거나, 이력에서 기존 견적을
                선택하세요.
              </p>
            </div>
          )}
        </section>
      </main>

      <footer>
        <span>
          가격 데이터는 스케줄러가 매일 1회 동기화한 캐시(PriceSnapshot) 기준이며, 실제 청구액과 다를
          수 있습니다.
        </span>
      </footer>
    </div>
  );
}
