-- 샘플 가격 데이터 (spring.profiles.active=local,sample 일 때만 적재).
-- 클라우드 자격증명/네트워크 없이 UI와 견적 로직을 확인하기 위한 용도이며,
-- 실제 청구 단가가 아니다. 운영에서는 PriceSyncScheduler가 채운 데이터를 사용한다.

INSERT INTO vendor (name) SELECT 'AWS'   WHERE NOT EXISTS (SELECT 1 FROM vendor WHERE name = 'AWS');
INSERT INTO vendor (name) SELECT 'AZURE' WHERE NOT EXISTS (SELECT 1 FROM vendor WHERE name = 'AZURE');

INSERT INTO price_snapshot (vendor_id, sku, vcpu, memory_gb, region, os, hourly_cost_usd, monthly_cost_usd, synced_at)
SELECT v.id, s.sku, s.vcpu, s.memory_gb, s.region, s.os, s.hourly, s.hourly * 730, CURRENT_TIMESTAMP
FROM (
    SELECT 'AWS' AS vendor, 't3.medium'   AS sku, 2  AS vcpu, 4   AS memory_gb, 'ap-northeast-2' AS region, 'LINUX' AS os, 0.0520 AS hourly UNION ALL
    SELECT 'AWS', 't3.large',    2,  8,  'ap-northeast-2', 'LINUX', 0.1040 UNION ALL
    SELECT 'AWS', 't3.xlarge',   4,  16, 'ap-northeast-2', 'LINUX', 0.2080 UNION ALL
    SELECT 'AWS', 't3.2xlarge',  8,  32, 'ap-northeast-2', 'LINUX', 0.4160 UNION ALL
    SELECT 'AWS', 'm5.large',    2,  8,  'ap-northeast-2', 'LINUX', 0.1180 UNION ALL
    SELECT 'AWS', 'm5.xlarge',   4,  16, 'ap-northeast-2', 'LINUX', 0.2360 UNION ALL
    SELECT 'AWS', 'm5.2xlarge',  8,  32, 'ap-northeast-2', 'LINUX', 0.4720 UNION ALL
    SELECT 'AWS', 'm5.4xlarge',  16, 64, 'ap-northeast-2', 'LINUX', 0.9440 UNION ALL
    SELECT 'AWS', 'r5.xlarge',   4,  32, 'ap-northeast-2', 'LINUX', 0.3040 UNION ALL
    SELECT 'AWS', 'c5.2xlarge',  8,  16, 'ap-northeast-2', 'LINUX', 0.4280 UNION ALL
    SELECT 'AZURE', 'Standard_B2s',       2,  4,  'koreacentral', 'LINUX', 0.0498 UNION ALL
    SELECT 'AZURE', 'Standard_B2ms',      2,  8,  'koreacentral', 'LINUX', 0.0996 UNION ALL
    SELECT 'AZURE', 'Standard_D2s_v5',    2,  8,  'koreacentral', 'LINUX', 0.1120 UNION ALL
    SELECT 'AZURE', 'Standard_D4s_v5',    4,  16, 'koreacentral', 'LINUX', 0.2240 UNION ALL
    SELECT 'AZURE', 'Standard_D8s_v5',    8,  32, 'koreacentral', 'LINUX', 0.4480 UNION ALL
    SELECT 'AZURE', 'Standard_D16s_v5',   16, 64, 'koreacentral', 'LINUX', 0.8960 UNION ALL
    SELECT 'AZURE', 'Standard_E4s_v5',    4,  32, 'koreacentral', 'LINUX', 0.2840 UNION ALL
    SELECT 'AZURE', 'Standard_F4s_v2',    4,  8,  'koreacentral', 'LINUX', 0.1960 UNION ALL
    SELECT 'AZURE', 'Standard_F8s_v2',    8,  16, 'koreacentral', 'LINUX', 0.3920
) s
JOIN vendor v ON v.name = s.vendor
WHERE NOT EXISTS (
    SELECT 1 FROM price_snapshot p
    WHERE p.vendor_id = v.id AND p.sku = s.sku AND p.region = s.region AND p.os = s.os
);
