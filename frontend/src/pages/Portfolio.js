import { useState } from "react";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import { usePortfolios, usePortfolioDetail, usePortfolioMutations } from "../hooks/queries";
import { fetchDividendsForPosition } from "../api/dividend";
import styles from "../css/Portfolio.module.css";

export default function Portfolio() {
  const { t } = useTranslation();
  const [selectedPortfolio, setSelectedPortfolio] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showAddPositionModal, setShowAddPositionModal] = useState(false);
  const toast = useToast();

  // React Query hooks
  const { data: portfolios = [], isLoading: loadingList } = usePortfolios();
  const { data: portfolioDetail, isLoading: loadingDetail } = usePortfolioDetail(selectedPortfolio);
  const mutations = usePortfolioMutations();
  const loading = loadingList || loadingDetail;

  // Form states
  const [createForm, setCreateForm] = useState({
    name: "",
    description: "",
    targetReturn: "",
    riskProfile: "moderate"
  });

  const [positionForm, setPositionForm] = useState({
    ticker: "",
    market: "US",
    quantity: "",
    entryPrice: "",
    entryDate: "",
    notes: ""
  });

  const handleCreatePortfolio = async (e) => {
    e.preventDefault();
    try {
      await mutations.create.mutateAsync({
        ...createForm,
        targetReturn: createForm.targetReturn ? parseFloat(createForm.targetReturn) : null
      });
      toast.success(t("portfolio.portfolioCreated"));
      setShowCreateModal(false);
      setCreateForm({ name: "", description: "", targetReturn: "", riskProfile: "moderate" });
    } catch (e) {
      toast.error(e.message || t("portfolio.failedToCreatePortfolio"));
    }
  };

  const handleDeletePortfolio = async (id) => {
    if (!window.confirm(t("portfolio.confirmDeletePortfolio"))) return;

    try {
      await mutations.remove.mutateAsync(id);
      toast.success(t("portfolio.portfolioDeleted"));
      setSelectedPortfolio(null);
    } catch (e) {
      toast.error(e.message || t("portfolio.failedToDeletePortfolio"));
    }
  };

  const handleAddPosition = async (e) => {
    e.preventDefault();
    try {
      const result = await mutations.addPos.mutateAsync({
        portfolioId: selectedPortfolio,
        data: {
          ...positionForm,
          quantity: parseFloat(positionForm.quantity),
          entryPrice: parseFloat(positionForm.entryPrice),
          entryDate: positionForm.entryDate || null
        }
      });

      // Automatically fetch dividends for the newly added position
      if (result?.positionId) {
        try {
          await fetchDividendsForPosition(result.positionId);
        } catch (divError) {
          console.warn("Failed to fetch dividends for position:", divError);
          // Don't fail the whole operation if dividend fetch fails
        }
      }

      toast.success(t("portfolio.positionAdded"));
      setShowAddPositionModal(false);
      setPositionForm({ ticker: "", market: "US", quantity: "", entryPrice: "", entryDate: "", notes: "" });
    } catch (e) {
      toast.error(e.message || t("portfolio.failedToAddPosition"));
    }
  };

  const handleDeletePosition = async (positionId) => {
    if (!window.confirm(t("portfolio.confirmRemovePosition"))) return;

    try {
      await mutations.removePos.mutateAsync({ portfolioId: selectedPortfolio, positionId });
      toast.success(t("portfolio.positionRemoved"));
    } catch (e) {
      toast.error(e.message || t("portfolio.failedToRemovePosition"));
    }
  };

  if (loading && !portfolios.length) {
    return <div className={styles.loading}>{t("portfolio.loadingPortfolios")}</div>;
  }

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h1>{t("portfolio.myPortfolios")}</h1>
        <button className={styles.createBtn} onClick={() => setShowCreateModal(true)}>
          + {t("portfolio.newPortfolio")}
        </button>
      </header>

      {!selectedPortfolio ? (
        // Portfolio List View
        <div className={styles.grid}>
          {portfolios.map(p => (
            <div key={p.id} className={styles.card} onClick={() => setSelectedPortfolio(p.id)}>
              <h3>{p.name}</h3>
              {p.description && <p className={styles.description}>{p.description}</p>}

              <div className={styles.metrics}>
                <div className={styles.metric}>
                  <span className={styles.label}>{t("portfolio.positions")}</span>
                  <span className={styles.value}>{p.positionCount}</span>
                </div>
                <div className={styles.metric}>
                  <span className={styles.label}>{t("portfolio.totalValue")}</span>
                  <span className={styles.value}>${p.totalValue?.toFixed(2) || "0.00"}</span>
                </div>
                <div className={styles.metric}>
                  <span className={styles.label}>{t("portfolio.return")}</span>
                  <span className={p.totalReturnPercent >= 0 ? styles.positive : styles.negative}>
                    {p.totalReturnPercent?.toFixed(2)}%
                  </span>
                </div>
              </div>

              <div className={styles.meta}>
                <span>{t("portfolio.risk")}: {p.riskProfile}</span>
                {p.targetReturn && <span>{t("portfolio.target")}: {p.targetReturn}%</span>}
              </div>
            </div>
          ))}

          {portfolios.length === 0 && (
            <div className={styles.empty}>
              <p>{t("portfolio.noPortfolios")}</p>
            </div>
          )}
        </div>
      ) : (
        // Portfolio Detail View
        <div className={styles.detail}>
          <div className={styles.detailHeader}>
            <button className={styles.backBtn} onClick={() => {
              setSelectedPortfolio(null);
            }}>
              ← {t("back")}
            </button>
            <h2>{portfolioDetail?.name}</h2>
            <div className={styles.actions}>
              <button className={styles.addBtn} onClick={() => setShowAddPositionModal(true)}>
                + {t("portfolio.addPosition")}
              </button>
              <button className={styles.deleteBtn} onClick={() => handleDeletePortfolio(selectedPortfolio)}>
                {t("portfolio.deletePortfolio")}
              </button>
            </div>
          </div>

          {portfolioDetail && (
            <>
              {/* Performance Summary */}
              <div className={styles.performance}>
                <div className={styles.performanceCard}>
                  <span className={styles.perfLabel}>{t("portfolio.totalValue")}</span>
                  <span className={styles.perfValue}>${portfolioDetail.performance.totalValue?.toFixed(2)}</span>
                </div>
                <div className={styles.performanceCard}>
                  <span className={styles.perfLabel}>{t("portfolio.totalCost")}</span>
                  <span className={styles.perfValue}>${portfolioDetail.performance.totalCost?.toFixed(2)}</span>
                </div>
                <div className={styles.performanceCard}>
                  <span className={styles.perfLabel}>{t("portfolio.totalReturn")}</span>
                  <span className={portfolioDetail.performance.totalReturnPercent >= 0 ? styles.positive : styles.negative}>
                    ${portfolioDetail.performance.totalReturn?.toFixed(2)} ({portfolioDetail.performance.totalReturnPercent?.toFixed(2)}%)
                  </span>
                </div>
              </div>

              {/* Positions Table */}
              <div className={styles.positions}>
                <h3>{t("portfolio.positions")}</h3>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>{t("portfolio.ticker")}</th>
                      <th>{t("portfolio.market")}</th>
                      <th>{t("portfolio.quantity")}</th>
                      <th>{t("portfolio.entryPrice")}</th>
                      <th>{t("portfolio.currentPrice")}</th>
                      <th>{t("portfolio.currentValue")}</th>
                      <th>{t("portfolio.gainLoss")}</th>
                      <th>{t("portfolio.action")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {portfolioDetail.positions.map(pos => (
                      <tr key={pos.id}>
                        <td><strong>{pos.ticker}</strong></td>
                        <td>{pos.market}</td>
                        <td>{pos.quantity}</td>
                        <td>${pos.entryPrice?.toFixed(2)}</td>
                        <td>${pos.currentPrice?.toFixed(2)}</td>
                        <td>${pos.currentValue?.toFixed(2)}</td>
                        <td className={pos.unrealizedGainPercent >= 0 ? styles.positive : styles.negative}>
                          ${pos.unrealizedGain?.toFixed(2)} ({pos.unrealizedGainPercent?.toFixed(2)}%)
                        </td>
                        <td>
                          <button className={styles.btnDelete} onClick={() => handleDeletePosition(pos.id)}>
                            {t("portfolio.remove")}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {portfolioDetail.positions.length === 0 && (
                  <div className={styles.empty}>
                    <p>{t("portfolio.noPositions")}</p>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}

      {/* Create Portfolio Modal */}
      {showCreateModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>{t("portfolio.createNewPortfolio")}</h3>
            <form onSubmit={handleCreatePortfolio}>
              <input
                type="text"
                placeholder={t("portfolio.portfolioNameRequired")}
                value={createForm.name}
                onChange={(e) => setCreateForm({...createForm, name: e.target.value})}
                required
                className={styles.input}
              />
              <textarea
                placeholder={t("portfolio.description")}
                value={createForm.description}
                onChange={(e) => setCreateForm({...createForm, description: e.target.value})}
                className={styles.textarea}
              />
              <input
                type="number"
                step="0.01"
                placeholder={t("portfolio.targetAnnualReturn")}
                value={createForm.targetReturn}
                onChange={(e) => setCreateForm({...createForm, targetReturn: e.target.value})}
                className={styles.input}
              />
              <select
                value={createForm.riskProfile}
                onChange={(e) => setCreateForm({...createForm, riskProfile: e.target.value})}
                className={styles.select}
              >
                <option value="conservative">{t("portfolio.conservative")}</option>
                <option value="moderate">{t("portfolio.moderate")}</option>
                <option value="aggressive">{t("portfolio.aggressive")}</option>
              </select>

              <div className={styles.modalActions}>
                <button type="submit" className={styles.btnPrimary}>{t("portfolio.create")}</button>
                <button type="button" className={styles.btnSecondary} onClick={() => setShowCreateModal(false)}>
                  {t("cancel")}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Position Modal */}
      {showAddPositionModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>{t("portfolio.addPosition")}</h3>
            <form onSubmit={handleAddPosition}>
              <input
                type="text"
                placeholder={t("portfolio.tickerPlaceholder")}
                value={positionForm.ticker}
                onChange={(e) => setPositionForm({...positionForm, ticker: e.target.value.toUpperCase()})}
                required
                className={styles.input}
              />
              <select
                value={positionForm.market}
                onChange={(e) => setPositionForm({...positionForm, market: e.target.value})}
                className={styles.select}
              >
                <option value="US">{t("portfolio.usMarket")}</option>
                <option value="KR">{t("portfolio.krMarket")}</option>
              </select>
              <input
                type="number"
                step="0.0001"
                placeholder={t("portfolio.quantityRequired")}
                value={positionForm.quantity}
                onChange={(e) => setPositionForm({...positionForm, quantity: e.target.value})}
                required
                className={styles.input}
              />
              <input
                type="number"
                step="0.01"
                placeholder={t("portfolio.entryPriceRequired")}
                value={positionForm.entryPrice}
                onChange={(e) => setPositionForm({...positionForm, entryPrice: e.target.value})}
                required
                className={styles.input}
              />
              <input
                type="date"
                placeholder={t("portfolio.entryDate")}
                value={positionForm.entryDate}
                onChange={(e) => setPositionForm({...positionForm, entryDate: e.target.value})}
                className={styles.input}
              />
              <textarea
                placeholder={t("portfolio.notes")}
                value={positionForm.notes}
                onChange={(e) => setPositionForm({...positionForm, notes: e.target.value})}
                className={styles.textarea}
              />

              <div className={styles.modalActions}>
                <button type="submit" className={styles.btnPrimary}>{t("portfolio.addPosition")}</button>
                <button type="button" className={styles.btnSecondary} onClick={() => setShowAddPositionModal(false)}>
                  {t("cancel")}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
