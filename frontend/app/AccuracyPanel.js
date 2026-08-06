'use client';

import { useEffect, useState } from 'react';

import AccuracyReportView from './AccuracyReportView';
import { labelInfo } from './labels';
import styles from './styles.module.css';

/**
 * 저장소에 커밋된 정확도 측정 결과를 보는 화면.
 *
 * 제공 자료 PDF나 Gemini API 키가 없어도 수치를 볼 수 있어야 해서, 다시 재지 않고 저장된 값을 읽는다.
 *
 * @returns {JSX.Element} 저장된 측정 결과
 */
export default function AccuracyPanel() {
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    /**
     * 저장된 측정 결과를 불러온다.
     *
     * @returns {Promise<void>}
     */
    async function load() {
      try {
        const response = await fetch('/api/accuracy');
        const body = await response.json();
        if (!response.ok) {
          setError(body.message ?? '측정 결과를 불러오지 못했습니다.');
          return;
        }
        setResult(body);
      } catch {
        setError('백엔드에 연결하지 못했습니다. 8080 포트에서 서버가 켜져 있는지 확인해 주세요.');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className={styles.progressBox}>
        <span className={styles.spinner} />
        <strong>불러오는 중입니다…</strong>
      </div>
    );
  }

  if (error) {
    return <div className={styles.errorBox}>{error}</div>;
  }

  const wrongPages = result.pages.filter((page) => page.actual !== page.predicted);

  return (
    <div className={styles.resultBox}>
      <h2 className={styles.fileName}>
        {result.packageId}
        <span className={styles.badgeStored}>저장된 측정값</span>
      </h2>
      <p className={styles.hint}>
        처리 경로 {result.pipeline} · 제공 자료로 미리 측정해 저장소에 커밋해 둔 값입니다.
        직접 재보려면 <strong>분류하기</strong> 탭에서 정답지 대조를 켜고 올리세요.
      </p>

      <AccuracyReportView report={result.report} />

      <h3 className={styles.sectionTitle}>정답과 다르게 판정한 페이지</h3>
      {wrongPages.length === 0 ? (
        <p className={styles.hint}>모든 페이지가 정답과 일치합니다.</p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.num}>쪽</th>
              <th>정답</th>
              <th>판정</th>
              <th>근거</th>
            </tr>
          </thead>
          <tbody>
            {wrongPages.map((page) => (
              <tr key={page.page}>
                <td className={styles.num}>{page.page}</td>
                <td>{labelInfo(page.actual).name}</td>
                <td>{labelInfo(page.predicted).name}</td>
                <td className={styles.evidence}>{page.evidence}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
