'use client';

import { useEffect, useState } from 'react';

import AccuracyReportView from './AccuracyReportView';
import { DECIDED_BY, labelInfo } from './labels';
import styles from './styles.module.css';

/** 정답지가 커밋되어 있는 패키지. 대조 스위치를 켜면 이 정답지로 채점한다 */
const GROUND_TRUTH_ID = 'package_01';

/**
 * PDF를 올려 분류 결과를 보는 화면.
 *
 * @returns {JSX.Element} 업로드 영역과 결과 영역
 */
export default function ClassifyPanel() {
  const [file, setFile] = useState(null);
  const [compareGroundTruth, setCompareGroundTruth] = useState(false);
  const [running, setRunning] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  // 처리에 1~2분이 걸려서, 경과 시간을 보여 주지 않으면 멈춘 것처럼 보인다
  useEffect(() => {
    if (!running) {
      return undefined;
    }
    const timer = setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
    return () => clearInterval(timer);
  }, [running]);

  /**
   * 고른 PDF를 백엔드에 올려 분류를 요청한다.
   *
   * 요청이 끝날 때까지 버튼을 잠근다. 같은 파일을 두 번 올리면 1~2분짜리 작업이 겹쳐 돌기 때문이다.
   *
   * @returns {Promise<void>}
   */
  async function runClassify() {
    setRunning(true);
    setElapsed(0);
    setResult(null);
    setError(null);

    const form = new FormData();
    form.append('file', file);
    const query = compareGroundTruth ? `?groundTruth=${GROUND_TRUTH_ID}` : '';

    try {
      const response = await fetch(`/api/classify${query}`, { method: 'POST', body: form });
      const body = await response.json();
      if (!response.ok) {
        // 백엔드가 실패 사유를 한글로 내려주므로 그대로 띄운다
        setError(body.message ?? '분류에 실패했습니다.');
        return;
      }
      setResult(body);
    } catch {
      setError('백엔드에 연결하지 못했습니다. 8080 포트에서 서버가 켜져 있는지 확인해 주세요.');
    } finally {
      setRunning(false);
    }
  }

  return (
    <div>
      <div className={styles.uploadBox}>
        <div className={styles.uploadRow}>
          <input
            type="file"
            accept="application/pdf"
            disabled={running}
            onChange={(event) => setFile(event.target.files[0] ?? null)}
          />
          <button
            type="button"
            className={styles.primaryButton}
            disabled={!file || running}
            onClick={runClassify}
          >
            {running ? '분류 중…' : '분류 시작'}
          </button>
        </div>

        <label className={styles.checkboxRow}>
          <input
            type="checkbox"
            checked={compareGroundTruth}
            disabled={running}
            onChange={(event) => setCompareGroundTruth(event.target.checked)}
          />
          <span>
            <strong>{GROUND_TRUTH_ID}</strong> 정답지와 대조해 정확도까지 재기
          </span>
        </label>
        <p className={styles.hint}>
          제공 자료의 {GROUND_TRUTH_ID} 셔플본을 올릴 때만 켜세요. 다른 PDF에 켜면 채점이 맞지 않습니다.
        </p>
      </div>

      {running && (
        <div className={styles.progressBox}>
          <span className={styles.spinner} />
          <div>
            <strong>분류하는 중입니다… {elapsed}초</strong>
            <p className={styles.hint}>
              페이지마다 텍스트를 읽고, 스캔 페이지는 OCR을, 규칙이 보류한 페이지는 LLM을 거칩니다.
              39페이지 기준 1~2분 걸립니다.
            </p>
          </div>
        </div>
      )}

      {error && <div className={styles.errorBox}>{error}</div>}

      {!running && !result && !error && (
        <div className={styles.emptyBox}>
          PDF를 고르고 <strong>분류 시작</strong>을 누르면 페이지별 판정과 문서 그룹핑 결과가 여기 나옵니다.
        </div>
      )}

      {result && <ClassifyResult result={result} />}
    </div>
  );
}

/**
 * 분류 결과 전체를 그린다.
 *
 * 방금 올려서 받은 결과와 저장소에 커밋된 결과가 같은 모양이라 두 화면이 이 컴포넌트를 함께 쓴다.
 * 어느 쪽 수치인지는 배지와 안내문으로만 갈린다.
 *
 * @param {object} props
 * @param {object} props.result 백엔드의 ClassificationResult
 * @param {boolean} [props.stored] 커밋된 결과를 그리는지 여부. 기본값은 방금 측정한 결과
 * @returns {JSX.Element} 요약 · 색 띠 · 문서 목록 · 페이지 표 · (있으면) 정확도
 */
export function ClassifyResult({ result, stored }) {
  const ruleCount = result.pages.filter((page) => page.decidedBy === 'RULE').length;
  const llmCount = result.pages.filter((page) => page.decidedBy === 'LLM').length;
  const ocrCount = result.pages.filter((page) => page.ocrUsed).length;

  return (
    <div className={styles.resultBox}>
      <h2 className={styles.fileName}>{result.fileName}</h2>
      <div className={styles.summaryRow}>
        <SummaryItem label="전체 페이지" value={`${result.totalPages}장`} />
        <SummaryItem label="복원한 문서" value={`${result.documents.length}개`} />
        <SummaryItem label="규칙 판정" value={`${ruleCount}장`} />
        <SummaryItem label="LLM 판정" value={`${llmCount}장`} />
        <SummaryItem label="OCR 사용" value={`${ocrCount}장`} />
      </div>

      <h3 className={styles.sectionTitle}>페이지 순서대로 본 라벨</h3>
      <PageStrip pages={result.pages} />

      <h3 className={styles.sectionTitle}>복원한 문서</h3>
      <p className={styles.hint}>
        아래 번호는 문서 안에서의 장 순서입니다. 페이지가 섞여 있어 시작 쪽이 끝 쪽보다 뒤 번호일 수 있습니다.
      </p>
      <DocumentList documents={result.documents} ungroupedPages={result.ungroupedPages} />

      <h3 className={styles.sectionTitle}>페이지별 판정</h3>
      <PageTable pages={result.pages} />

      {result.accuracy && (
        <>
          <h3 className={styles.sectionTitle}>
            정확도
            <span className={stored ? styles.badgeStored : styles.badgeLive}>
              {stored ? '저장된 측정값' : '방금 측정'}
            </span>
          </h3>
          <p className={styles.hint}>
            {stored
              ? `제공 자료로 미리 돌려 ${GROUND_TRUTH_ID} 정답지와 대조한 값입니다.`
              : `이번 업로드 결과를 ${GROUND_TRUTH_ID} 정답지와 대조해 계산한 값입니다.`}
          </p>
          <AccuracyReportView report={result.accuracy} />
        </>
      )}
    </div>
  );
}

/**
 * 요약 숫자 한 칸.
 *
 * @param {object} props
 * @param {string} props.label 항목 이름
 * @param {string} props.value 보여 줄 값
 * @returns {JSX.Element} 요약 카드
 */
function SummaryItem({ label, value }) {
  return (
    <div className={styles.summaryCard}>
      <span className={styles.summaryLabel}>{label}</span>
      <span className={styles.summaryValue}>{value}</span>
    </div>
  );
}

/**
 * 페이지를 PDF 순서대로 색 띠로 늘어놓는다.
 *
 * 표만 보면 어떤 유형이 어디에 흩어져 있는지 눈에 안 들어와서, 섞여 있다는 사실 자체를 보여 준다.
 *
 * @param {object} props
 * @param {object[]} props.pages 페이지별 판정 결과
 * @returns {JSX.Element} 색 띠와 범례
 */
function PageStrip({ pages }) {
  const usedLabels = [...new Set(pages.map((page) => page.label))];

  return (
    <div>
      <div className={styles.strip}>
        {pages.map((page) => (
          <div
            key={page.pageNumber}
            className={styles.stripCell}
            style={{ background: labelInfo(page.label).color }}
            title={`${page.pageNumber}쪽 · ${labelInfo(page.label).name}`}
          >
            {page.pageNumber}
          </div>
        ))}
      </div>
      <div className={styles.legend}>
        {usedLabels.map((label) => (
          <span key={label} className={styles.labelChip}>
            <span className={styles.labelDot} style={{ background: labelInfo(label).color }} />
            {labelInfo(label).name}
          </span>
        ))}
      </div>
    </div>
  );
}

/**
 * 다시 묶어 낸 문서들을 보여 준다.
 *
 * @param {object} props
 * @param {object[]} props.documents 문서 목록
 * @param {number[]} props.ungroupedPages 어느 문서에도 넣지 못한 페이지 번호
 * @returns {JSX.Element} 문서 카드 목록
 */
function DocumentList({ documents, ungroupedPages }) {
  return (
    <div>
      {documents.length === 0 && (
        <p className={styles.hint}>묶어 낸 문서가 없습니다.</p>
      )}

      {documents.map((document, index) => (
        <div key={index} className={styles.documentCard}>
          <div className={styles.documentHead}>
            <span className={styles.labelChip}>
              <span
                className={styles.labelDot}
                style={{ background: labelInfo(document.label).color }}
              />
              <strong>{labelInfo(document.label).name}</strong>
            </span>
            <span className={styles.hint}>
              {document.pages.length}장 · 시작 {document.startPage}쪽 · 끝 {document.endPage}쪽
            </span>
          </div>
          <div className={styles.pageOrder}>
            {document.pages.map((pageNumber) => (
              <span key={pageNumber} className={styles.pageTag}>
                {pageNumber}
              </span>
            ))}
          </div>
        </div>
      ))}

      {ungroupedPages.length > 0 && (
        <div className={styles.warnBox}>
          어느 문서에도 넣지 못한 페이지: {ungroupedPages.join(', ')}쪽
          <p className={styles.hint}>
            페이지 마커가 없고 LLM도 답하지 못하면, 근거 없이 묶는 대신 이렇게 남깁니다.
          </p>
        </div>
      )}
    </div>
  );
}

/**
 * 페이지별 판정 결과 표.
 *
 * 라벨만 주지 않고 판정 경로와 근거를 함께 보여 준다. 규칙과 LLM이 각각 몇 장을 맡았는지,
 * 그 페이지가 왜 그 라벨을 받았는지 확인할 수 있어야 하기 때문이다.
 *
 * @param {object} props
 * @param {object[]} props.pages 페이지별 판정 결과
 * @returns {JSX.Element} 판정 표
 */
function PageTable({ pages }) {
  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th className={styles.num}>쪽</th>
          <th>라벨</th>
          <th>판정 경로</th>
          <th>페이지 마커</th>
          <th>근거</th>
        </tr>
      </thead>
      <tbody>
        {pages.map((page) => (
          <tr key={page.pageNumber}>
            <td className={styles.num}>{page.pageNumber}</td>
            <td>
              <span className={styles.labelChip}>
                <span
                  className={styles.labelDot}
                  style={{ background: labelInfo(page.label).color }}
                />
                {labelInfo(page.label).name}
              </span>
            </td>
            <td>
              {DECIDED_BY[page.decidedBy]}
              {page.ocrUsed && <> <span className={styles.badgeOcr}>OCR</span></>}
            </td>
            <td>{page.marker ?? '—'}</td>
            <td className={styles.evidence}>{page.evidence}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
