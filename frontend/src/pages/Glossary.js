import { useState, useMemo } from "react";
import styles from "../css/Glossary.module.css";
import glossaryData from "../data/glossary.json";

const categoryNames = {
  technical: "기술적 분석",
  fundamental: "기본적 분석",
  risk: "리스크 지표",
  sentiment: "시장 심리",
  strategy: "투자 전략",
  trading: "거래 타입",
  portfolio: "포트폴리오 관리",
};

export default function Glossary() {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("all");

  const categories = useMemo(() => {
    const cats = new Set();
    Object.values(glossaryData.terms).forEach((term) => {
      if (term.category) cats.add(term.category);
    });
    return Array.from(cats);
  }, []);

  const filteredTerms = useMemo(() => {
    const query = searchQuery.toLowerCase();
    const entries = Object.entries(glossaryData.terms);

    return entries
      .filter(([key, term]) => {
        const matchesSearch =
          !query ||
          term.name.toLowerCase().includes(query) ||
          term.definition.toLowerCase().includes(query) ||
          key.toLowerCase().includes(query);

        const matchesCategory =
          selectedCategory === "all" || term.category === selectedCategory;

        return matchesSearch && matchesCategory;
      })
      .sort((a, b) => a[1].name.localeCompare(b[1].name));
  }, [searchQuery, selectedCategory]);

  const groupedTerms = useMemo(() => {
    const grouped = {};
    filteredTerms.forEach(([key, term]) => {
      const cat = term.category || "other";
      if (!grouped[cat]) grouped[cat] = [];
      grouped[cat].push([key, term]);
    });
    return grouped;
  }, [filteredTerms]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>용어 사전</h1>
        <p className={styles.subtitle}>
          주식 투자와 분석에 사용되는 주요 용어들을 확인하세요
        </p>
      </div>

      <div className={styles.controls}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="용어 검색..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />

        <div className={styles.categoryTabs}>
          <button
            className={selectedCategory === "all" ? styles.tabActive : styles.tab}
            onClick={() => setSelectedCategory("all")}
          >
            전체
          </button>
          {categories.map((cat) => (
            <button
              key={cat}
              className={selectedCategory === cat ? styles.tabActive : styles.tab}
              onClick={() => setSelectedCategory(cat)}
            >
              {categoryNames[cat] || cat}
            </button>
          ))}
        </div>
      </div>

      {filteredTerms.length === 0 ? (
        <div className={styles.empty}>
          <div className={styles.emptyIcon}>🔍</div>
          <div className={styles.emptyText}>검색 결과가 없습니다</div>
        </div>
      ) : (
        <div className={styles.content}>
          {Object.entries(groupedTerms).map(([category, terms]) => (
            <div key={category} className={styles.categorySection}>
              <h2 className={styles.categoryTitle}>
                {categoryNames[category] || category}
              </h2>
              <div className={styles.termsList}>
                {terms.map(([key, term]) => (
                  <div key={key} className={styles.termCard}>
                    <div className={styles.termName}>{term.name}</div>
                    <div className={styles.termDefinition}>{term.definition}</div>
                    {term.category && (
                      <div className={styles.termBadge}>
                        {categoryNames[term.category] || term.category}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className={styles.footer}>
        <p className={styles.footerText}>
          총 <strong>{Object.keys(glossaryData.terms).length}개</strong>의 용어가
          등록되어 있습니다
        </p>
      </div>
    </div>
  );
}
