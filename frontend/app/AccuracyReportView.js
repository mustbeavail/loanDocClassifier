import { LABEL_ORDER, labelInfo, toPercent } from './labels';
import styles from './styles.module.css';

/**
 * 정확도 측정 결과를 표로 그린다.
 *
 * 업로드해서 방금 잰 결과와 저장소에 커밋된 결과가 같은 모양이라 두 화면이 이 컴포넌트를 함께 쓴다.
 * 어느 쪽 수치인지는 이 컴포넌트가 아니라 부르는 쪽에서 밝힌다.
 *
 * @param {object} props
 * @param {object} props.report 백엔드의 AccuracyReport
 * @returns {JSX.Element} 요약 숫자 + 라벨별 지표 표 + 혼동행렬
 */
export default function AccuracyReportView({ report }) {
  return (
    <div>
      <div className={styles.summaryRow}>
        <Summary label="전체 정확도" value={toPercent(report.accuracy)} strong />
        <Summary label="맞은 페이지" value={`${report.correctPages} / ${report.totalPages}`} />
        <Summary label="판정 보류" value={`${report.undecidedPages}장`} />
      </div>

      <h3 className={styles.sectionTitle}>라벨별 지표</h3>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>라벨</th>
            <th className={styles.num}>정답수</th>
            <th className={styles.num}>판정수</th>
            <th className={styles.num}>맞은수</th>
            <th className={styles.num}>정밀도</th>
            <th className={styles.num}>재현율</th>
            <th className={styles.num}>F1</th>
          </tr>
        </thead>
        <tbody>
          {report.labelMetrics.map((metrics) => (
            <tr key={metrics.label}>
              <td>
                <LabelChip label={metrics.label} />
              </td>
              <td className={styles.num}>{metrics.support}</td>
              <td className={styles.num}>{metrics.predicted}</td>
              <td className={styles.num}>{metrics.correct}</td>
              <td className={styles.num}>{metrics.precision.toFixed(3)}</td>
              <td className={styles.num}>{metrics.recall.toFixed(3)}</td>
              <td className={styles.num}>{metrics.f1.toFixed(3)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3 className={styles.sectionTitle}>혼동행렬</h3>
      <p className={styles.hint}>세로가 정답, 가로가 판정 결과입니다. 대각선이 맞힌 페이지입니다.</p>
      <ConfusionMatrix matrix={report.confusionMatrix} />
    </div>
  );
}

/**
 * 요약 숫자 한 칸.
 *
 * @param {object} props
 * @param {string} props.label 항목 이름
 * @param {string} props.value 보여 줄 값
 * @param {boolean} [props.strong] 강조 표시 여부. 전체 정확도처럼 가장 먼저 읽혀야 하는 값에 쓴다
 * @returns {JSX.Element} 요약 카드
 */
function Summary({ label, value, strong }) {
  return (
    <div className={styles.summaryCard}>
      <span className={styles.summaryLabel}>{label}</span>
      <span className={strong ? styles.summaryValueStrong : styles.summaryValue}>{value}</span>
    </div>
  );
}

/**
 * 라벨 이름을 색 점과 함께 보여 준다.
 *
 * @param {object} props
 * @param {string} props.label 라벨 값
 * @returns {JSX.Element} 색 점 + 한글 이름
 */
function LabelChip({ label }) {
  const info = labelInfo(label);
  return (
    <span className={styles.labelChip}>
      <span className={styles.labelDot} style={{ background: info.color }} />
      {info.name}
    </span>
  );
}

/**
 * 혼동행렬을 표로 그린다.
 *
 * 정답지에 없는 라벨까지 모두 그리면 0만 가득한 칸이 늘어나므로,
 * 실제로 정답이나 판정에 등장한 라벨만 골라 그린다.
 *
 * @param {object} props
 * @param {object} props.matrix 정답 라벨 → 판정 라벨 → 페이지 수
 * @returns {JSX.Element} 혼동행렬 표
 */
function ConfusionMatrix({ matrix }) {
  const used = LABEL_ORDER.filter((label) => {
    const isActual = Object.values(matrix[label] ?? {}).some((count) => count > 0);
    const isPredicted = Object.values(matrix).some((row) => row[label] > 0);
    return isActual || isPredicted;
  });

  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th>정답 \ 판정</th>
          {used.map((label) => (
            <th key={label} className={styles.num}>
              {labelInfo(label).name}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {used
          .filter((actual) => matrix[actual] !== undefined)
          .map((actual) => (
            <tr key={actual}>
              <th scope="row">
                <LabelChip label={actual} />
              </th>
              {used.map((predicted) => {
                const count = matrix[actual][predicted] ?? 0;
                const isCorrect = actual === predicted;
                return (
                  <td
                    key={predicted}
                    className={`${styles.num} ${isCorrect ? styles.matrixHit : ''}`}
                  >
                    {count === 0 ? '·' : count}
                  </td>
                );
              })}
            </tr>
          ))}
      </tbody>
    </table>
  );
}
