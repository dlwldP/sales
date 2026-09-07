import type { PageResponse, QuoteSummary } from '../types/quote';

interface Props {
  page: PageResponse<QuoteSummary> | null;
  selectedId: number | null;
  onSelect: (quoteId: number) => void;
  onPageChange: (page: number) => void;
}

const formatDate = (iso: string) => new Date(iso).toLocaleString('ko-KR');

export default function QuoteHistory({ page, selectedId, onSelect, onPageChange }: Props) {
  if (!page) {
    return (
      <div className="card">
        <h2>견적 이력</h2>
        <p className="muted">불러오는 중…</p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>견적 이력 ({page.totalElements})</h2>
      {page.content.length === 0 ? (
        <p className="muted">저장된 견적이 없습니다. 첫 견적을 산출해 보세요.</p>
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

      {page.totalPages > 1 && (
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
