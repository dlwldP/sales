import { quotePdfUrl } from '../api/client';
import { hasPrice } from '../types/quote';
import type { Quote } from '../types/quote';

interface Props {
  quote: Quote;
}

const usd = (value?: number | null) =>
  typeof value === 'number'
    ? `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '-';

export default function ComparisonTable({ quote }: Props) {
  // results는 서버에서 월 비용 오름차순으로 내려온다.
  const priced = quote.results.filter(hasPrice);
  const cheapest = priced.length > 0 ? priced[0].vendor : null;

  return (
    <div className="card">
      <div className="card-head">
        <h2>벤더별 비교</h2>
        <a className="button-link" href={quotePdfUrl(quote.quoteId)} download>
          견적서 PDF 내려받기
        </a>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>벤더</th>
              <th>매칭 SKU</th>
              <th className="num">컴퓨트 ($/월)</th>
              <th className="num">스토리지 ($/월)</th>
              <th className="num">합계 ($/월)</th>
            </tr>
          </thead>
          <tbody>
            {quote.results.map((item) => (
              <tr key={item.vendor} className={item.vendor === cheapest ? 'best' : undefined}>
                <td>
                  {item.vendor}
                  {item.vendor === cheapest && <span className="badge">최저가</span>}
                </td>
                <td>{item.matchedSku ?? <span className="muted">{item.note ?? '매칭 없음'}</span>}</td>
                <td className="num">{usd(item.computeCostUsd)}</td>
                <td className="num">{usd(item.storageCostUsd)}</td>
                <td className="num strong">{usd(item.monthlyCostUsd)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="hint">
        {quote.vcpu} vCPU / {quote.memoryGb}GB RAM / {quote.storageGb}GB 스토리지 · {quote.region} ·{' '}
        {quote.os} · 월 730시간 기준
      </p>
    </div>
  );
}
