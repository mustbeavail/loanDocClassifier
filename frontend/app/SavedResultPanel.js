'use client';

import { useEffect, useState } from 'react';

import { ClassifyResult } from './ClassifyPanel';
import styles from './styles.module.css';

/** 결과 파일이 커밋되어 있는 패키지들 */
const PACKAGES = ['package_01', 'package_02'];

/**
 * 저장소에 커밋된 분류·그룹핑 결과를 보는 화면.
 *
 * 제공 자료 PDF도 Gemini 키도 없는 심사자가 두 패키지 결과를 확인할 수 있어야 해서,
 * 다시 돌리지 않고 미리 산출해 커밋해 둔 파일을 읽는다.
 *
 * @returns {JSX.Element} 패키지 선택 + 저장된 결과
 */
export default function SavedResultPanel() {
  const [packageId, setPackageId] = useState(PACKAGES[0]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    /**
     * 고른 패키지의 저장된 결과를 불러온다.
     *
     * @returns {Promise<void>}
     */
    async function load() {
      setLoading(true);
      setResult(null);
      setError(null);
      try {
        const response = await fetch(`/api/results/${packageId}`);
        const body = await response.json();
        if (cancelled) {
          return;
        }
        if (!response.ok) {
          setError(body.message ?? '저장된 결과를 불러오지 못했습니다.');
          return;
        }
        setResult(body);
      } catch {
        if (!cancelled) {
          setError('백엔드에 연결하지 못했습니다. 8080 포트에서 서버가 켜져 있는지 확인해 주세요.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    load();

    // 패키지를 빠르게 바꾸면 앞선 응답이 뒤에 도착해 화면을 덮을 수 있다
    return () => {
      cancelled = true;
    };
  }, [packageId]);

  return (
    <div>
      <nav className={styles.tabs}>
        {PACKAGES.map((id) => (
          <button
            key={id}
            type="button"
            className={id === packageId ? styles.tabActive : styles.tab}
            onClick={() => setPackageId(id)}
          >
            {id}
          </button>
        ))}
      </nav>

      <p className={styles.hint}>
        제공 자료나 API 키가 없어도 볼 수 있도록, 미리 돌려 저장소에 커밋해 둔 결과를 읽습니다.
        직접 재보려면 <strong>분류하기</strong> 탭에서 PDF를 올리세요.
      </p>

      {loading && (
        <div className={styles.progressBox}>
          <span className={styles.spinner} />
          <strong>불러오는 중입니다…</strong>
        </div>
      )}

      {error && <div className={styles.errorBox}>{error}</div>}

      {result && <ClassifyResult result={result} stored />}
    </div>
  );
}
