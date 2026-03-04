import Tooltip from "./Tooltip";
import glossaryData from "../../data/glossary.json";

/**
 * 용어 컴포넌트 - 자동으로 툴팁 표시
 * @param {object} props
 * @param {string} props.term - 용어 키 (glossary.json의 key)
 * @param {React.ReactNode} props.children - 표시할 텍스트 (선택사항, 없으면 용어명 사용)
 * @param {"top" | "bottom" | "left" | "right"} props.position - 툴팁 위치
 */
export default function Term({ term, children, position = "top" }) {
  const termData = glossaryData.terms[term];

  if (!termData) {
    // 용어를 찾을 수 없으면 그냥 텍스트 반환
    return <>{children || term}</>;
  }

  const tooltipContent = (
    <div>
      <div style={{ fontWeight: "bold", marginBottom: "4px" }}>
        {termData.name}
      </div>
      <div>{termData.definition}</div>
    </div>
  );

  return (
    <Tooltip content={tooltipContent} position={position}>
      {children || termData.name}
    </Tooltip>
  );
}
