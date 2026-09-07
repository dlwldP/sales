import { useState } from 'react';
import type { Meta, OsType, QuoteCreateRequest, VendorType } from '../types/quote';

interface Props {
  meta: Meta | null;
  submitting: boolean;
  onSubmit: (request: QuoteCreateRequest) => void;
}

const DEFAULT_FORM: QuoteCreateRequest = {
  workloadName: '테스트 서버',
  vcpu: 4,
  memoryGb: 16,
  storageGb: 100,
  region: 'korea',
  os: 'LINUX',
  vendors: ['AWS', 'AZURE'],
};

const VENDOR_FALLBACK: VendorType[] = ['AWS', 'AZURE', 'GCP'];

export default function QuoteForm({ meta, submitting, onSubmit }: Props) {
  const [form, setForm] = useState<QuoteCreateRequest>(DEFAULT_FORM);

  const regions = meta?.regions ?? ['korea'];
  const vendors = meta?.vendors ?? VENDOR_FALLBACK;
  const osTypes = meta?.osTypes ?? (['LINUX', 'WINDOWS'] as OsType[]);

  const toggleVendor = (vendor: VendorType) => {
    setForm((prev) => ({
      ...prev,
      vendors: prev.vendors.includes(vendor)
        ? prev.vendors.filter((v) => v !== vendor)
        : [...prev.vendors, vendor],
    }));
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    onSubmit(form);
  };

  return (
    <form className="card" onSubmit={handleSubmit}>
      <h2>워크로드 스펙</h2>

      <label>
        워크로드명
        <input
          type="text"
          value={form.workloadName}
          maxLength={100}
          required
          onChange={(e) => setForm({ ...form, workloadName: e.target.value })}
        />
      </label>

      <div className="grid">
        <label>
          vCPU
          <input
            type="number"
            min={1}
            max={512}
            value={form.vcpu}
            required
            onChange={(e) => setForm({ ...form, vcpu: Number(e.target.value) })}
          />
        </label>
        <label>
          메모리 (GB)
          <input
            type="number"
            min={1}
            max={4096}
            value={form.memoryGb}
            required
            onChange={(e) => setForm({ ...form, memoryGb: Number(e.target.value) })}
          />
        </label>
        <label>
          스토리지 (GB)
          <input
            type="number"
            min={0}
            max={65536}
            value={form.storageGb}
            required
            onChange={(e) => setForm({ ...form, storageGb: Number(e.target.value) })}
          />
        </label>
      </div>

      <div className="grid">
        <label>
          리전
          <select value={form.region} onChange={(e) => setForm({ ...form, region: e.target.value })}>
            {regions.map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </select>
        </label>
        <label>
          OS
          <select
            value={form.os}
            onChange={(e) => setForm({ ...form, os: e.target.value as OsType })}
          >
            {osTypes.map((os) => (
              <option key={os} value={os}>
                {os}
              </option>
            ))}
          </select>
        </label>
      </div>

      <fieldset className="vendors">
        <legend>비교할 벤더</legend>
        {vendors.map((vendor) => (
          <label key={vendor} className="checkbox">
            <input
              type="checkbox"
              checked={form.vendors.includes(vendor)}
              onChange={() => toggleVendor(vendor)}
            />
            {vendor}
          </label>
        ))}
      </fieldset>

      <button type="submit" disabled={submitting || form.vendors.length === 0}>
        {submitting ? '견적 산출 중…' : '견적 산출'}
      </button>
      {form.vendors.length === 0 && <p className="hint">벤더를 1개 이상 선택하세요.</p>}
    </form>
  );
}
