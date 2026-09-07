import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { Quote } from '../types/quote';

interface Props {
  quote: Quote;
}

const VENDOR_COLORS: Record<string, string> = {
  AWS: '#ff9900',
  AZURE: '#0078d4',
  GCP: '#34a853',
};

export default function ComparisonChart({ quote }: Props) {
  const data = quote.results
    .filter((item) => item.monthlyCostUsd !== null)
    .map((item) => ({
      vendor: item.vendor,
      compute: item.computeCostUsd ?? 0,
      storage: item.storageCostUsd ?? 0,
      total: item.monthlyCostUsd ?? 0,
    }));

  if (data.length === 0) {
    return (
      <div className="card">
        <h2>월 비용 비교</h2>
        <p className="muted">차트로 표시할 가격 데이터가 없습니다. 가격 동기화 상태를 확인하세요.</p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>월 비용 비교 (USD)</h2>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis dataKey="vendor" />
          <YAxis tickFormatter={(value: number) => `$${value}`} />
          <Tooltip formatter={(value: number) => `$${value.toFixed(2)}`} />
          <Legend />
          {/* 막대 색은 벤더별 Cell로 지정하고, 범례 색이 검게 나오지 않도록 기본 fill을 함께 준다. */}
          <Bar dataKey="compute" name="컴퓨트" stackId="cost" fill="#9ca3af">
            {data.map((entry) => (
              <Cell key={entry.vendor} fill={VENDOR_COLORS[entry.vendor] ?? '#6b7280'} />
            ))}
          </Bar>
          <Bar dataKey="storage" name="스토리지" stackId="cost" fill="#cbd5e1" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
