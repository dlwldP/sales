import type { Meta, PageResponse, QuoteHistoryFilter, QuoteSummary } from '../types/quote';
import { EMPTY_FILTER } from '../types/quote';

interface Props {
  page: PageResponse<QuoteSummary> | null;
  meta: Meta | null;
  filter: QuoteHistoryFilter;
  selectedId: number | null;
  onFilterChange: (filter: QuoteHistoryFilter) => void;
  onSelect: (quoteId: number) => void;
  onPageChange: (page: number) => void;
}

const formatDate = (iso: string) => new Date(iso).toLocaleString('ko-KR');

export default function QuoteHistory({
  page,
  meta,
  filter,
  selectedId,
  onFilterChange,
  onSelect,
  onPageChange,
}: Props) {
  const hasFilter = Object.values(filter).some((value) => value !== '');
  const update = (patch: Partial<QuoteHistoryFilter>) => onFilterChange({ ...filter, ...patch });

  return (
    <div className="card">
      <div className="card-head">
        <h2>견적 이력{page ? ` (${page.totalElements})` : ''}</h2>
        {hasFilter && (
          <button type="button" className="link" onClick={() => onFilterChange(EMPTY_FILTER)}>
            필터 초기화
          </button>
        )}
      </div>

      <div className="filters">
        <label>
          리전
          <select value={filter.region} onChange={(e) => update({ region: e.target.value })}>
            <option value="">전체</option>
            {(meta?.regions ?? []).map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </select>
        </label>
        <label>
          벤더
          <select value={filter.vendor} onChange={(e) => update({ vendor: e.target.value })}>
            <option value="">전체</option>
            {(meta?.vendors ?? []).map((vendor) => (
              <option key={vendor} value={vendor}>
                {vendor}
              </option>
            ))}
          </select>
        </label>
        <label>
          시작일
          <input
            type="date"
            value={filter.from}
            max={filter.to || undefined}
            onChange={(e) => update({ from: e.target.value })}
          />
        </label>
        <label>
          종료일
          <input
            type="date"
            value={filter.to}
            min={filter.from || undefined}
            onChange={(e) => update({ to: e.target.value })}
          />
        </label>
      </div>

      {!page ? (
        <p className="muted">불러오는 중…</p>
      ) : page.content.length === 0 ? (
        <p className="muted">
          {hasFilter ? '조건에 맞는 견적이 없습니다.' : '저장된 견적이 없습니다. 첫 견적을 산출해 보세요.'}
        </p>
      ) : (
        <ul className="history">
          {page.content.map((quote) => (
            <li key={quote.quoteId}>
              <button
                type="button"
                className={quote.quoteId === selectedId ? 'history-item selected' : 'history-item'}
                onClick={() => onSelect(quote.quoteId)}
              >
                <span className="history-name">{quote.workloadName}</span>
                <span className="muted">
                  {quote.vcpu}vCPU · {quote.memoryGb}GB · {quote.region}
                </span>
                <span className="muted small">{formatDate(quote.createdAt)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {page && page.totalPages > 1 && (
        <div className="pagination">
          <button type="button" disabled={page.page === 0} onClick={() => onPageChange(page.page - 1)}>
            이전
          </button>
          <span>
            {page.page + 1} / {page.totalPages}
          </span>
          <button
            type="button"
            disabled={page.page >= page.totalPages - 1}
            onClick={() => onPageChange(page.page + 1)}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
