"""
섹터별 밸류에이션 밴드 상수
"""

SECTOR_BANDS = {
    "TECHNOLOGY": {
        "pe": {"low": 18, "high": 50, "weight": 0.45},
        "ps": {"low": 4, "high": 15, "weight": 0.25},
        "pb": {"low": 3, "high": 12, "weight": 0.10},
        "ev_ebitda": {"low": 12, "high": 35, "weight": 0.10},
        "roe_weight": 0.25, "opm_weight": 0.30,
        "quality": {"min_roe": 0.10, "max_debt_equity": 1.5, "fcf_margin": 0.22}
    },
    "HEALTHCARE": {
        "pe": {"low": 15, "high": 40, "weight": 0.45},
        "ps": {"low": 3, "high": 12, "weight": 0.25},
        "pb": {"low": 2, "high": 8, "weight": 0.15},
        "ev_ebitda": {"low": 10, "high": 30, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.20,
        "quality": {"min_roe": 0.08, "max_debt_equity": 1.0, "fcf_margin": 0.18}
    },
    "FINANCIAL SERVICES": {
        "pe": {"low": 8, "high": 18, "weight": 0.35},
        "ps": {"low": 1.5, "high": 5, "weight": 0.15},
        "pb": {"low": 0.8, "high": 2.5, "weight": 0.35},
        "ev_ebitda": {"low": 6, "high": 15, "weight": 0.05},
        "roe_weight": 0.30, "opm_weight": 0.15,
        "quality": {"min_roe": 0.10, "max_debt_equity": 10.0, "fcf_margin": 0.15}
    },
    "CONSUMER CYCLICAL": {
        "pe": {"low": 12, "high": 30, "weight": 0.45},
        "ps": {"low": 1.5, "high": 6, "weight": 0.25},
        "pb": {"low": 2, "high": 8, "weight": 0.15},
        "ev_ebitda": {"low": 8, "high": 20, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.20,
        "quality": {"min_roe": 0.12, "max_debt_equity": 1.5, "fcf_margin": 0.10}
    },
    "CONSUMER DEFENSIVE": {
        "pe": {"low": 15, "high": 28, "weight": 0.50},
        "ps": {"low": 1, "high": 4, "weight": 0.25},
        "pb": {"low": 2, "high": 6, "weight": 0.10},
        "ev_ebitda": {"low": 10, "high": 18, "weight": 0.10},
        "roe_weight": 0.15, "opm_weight": 0.25,
        "quality": {"min_roe": 0.15, "max_debt_equity": 1.0, "fcf_margin": 0.12}
    },
    "INDUSTRIALS": {
        "pe": {"low": 12, "high": 28, "weight": 0.45},
        "ps": {"low": 1.5, "high": 5, "weight": 0.25},
        "pb": {"low": 2, "high": 6, "weight": 0.15},
        "ev_ebitda": {"low": 8, "high": 18, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.20,
        "quality": {"min_roe": 0.12, "max_debt_equity": 1.2, "fcf_margin": 0.10}
    },
    "ENERGY": {
        "pe": {"low": 6, "high": 18, "weight": 0.40},
        "ps": {"low": 0.5, "high": 3, "weight": 0.25},
        "pb": {"low": 1, "high": 3, "weight": 0.20},
        "ev_ebitda": {"low": 4, "high": 12, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.25,
        "quality": {"min_roe": 0.08, "max_debt_equity": 0.8, "fcf_margin": 0.12}
    },
    "BASIC MATERIALS": {
        "pe": {"low": 8, "high": 20, "weight": 0.40},
        "ps": {"low": 1, "high": 4, "weight": 0.25},
        "pb": {"low": 1.5, "high": 4, "weight": 0.20},
        "ev_ebitda": {"low": 5, "high": 14, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.20,
        "quality": {"min_roe": 0.10, "max_debt_equity": 1.0, "fcf_margin": 0.10}
    },
    "UTILITIES": {
        "pe": {"low": 12, "high": 22, "weight": 0.45},
        "ps": {"low": 1.5, "high": 4, "weight": 0.20},
        "pb": {"low": 1, "high": 2.5, "weight": 0.20},
        "ev_ebitda": {"low": 8, "high": 14, "weight": 0.10},
        "roe_weight": 0.15, "opm_weight": 0.25,
        "quality": {"min_roe": 0.08, "max_debt_equity": 2.0, "fcf_margin": 0.14}
    },
    "REAL ESTATE": {
        "pe": {"low": 15, "high": 35, "weight": 0.30},
        "ps": {"low": 3, "high": 10, "weight": 0.20},
        "pb": {"low": 0.8, "high": 2, "weight": 0.35},
        "ev_ebitda": {"low": 12, "high": 25, "weight": 0.10},
        "roe_weight": 0.15, "opm_weight": 0.15,
        "quality": {"min_roe": 0.05, "max_debt_equity": 2.5, "fcf_margin": 0.08}
    },
    "COMMUNICATION SERVICES": {
        "pe": {"low": 15, "high": 40, "weight": 0.45},
        "ps": {"low": 2, "high": 8, "weight": 0.25},
        "pb": {"low": 2, "high": 8, "weight": 0.10},
        "ev_ebitda": {"low": 8, "high": 20, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.25,
        "quality": {"min_roe": 0.10, "max_debt_equity": 1.5, "fcf_margin": 0.18}
    },
    "DEFAULT": {
        "pe": {"low": 12, "high": 35, "weight": 0.45},
        "ps": {"low": 2, "high": 10, "weight": 0.25},
        "pb": {"low": 1.5, "high": 8, "weight": 0.15},
        "ev_ebitda": {"low": 8, "high": 20, "weight": 0.10},
        "roe_weight": 0.20, "opm_weight": 0.20,
        "quality": {"min_roe": 0.10, "max_debt_equity": 1.5, "fcf_margin": 0.10}
    }
}


def get_sector_bands(sector: str | None) -> dict:
    """섹터에 맞는 밴드 반환"""
    if not sector:
        return SECTOR_BANDS["DEFAULT"]
    key = sector.strip().upper()
    return SECTOR_BANDS.get(key, SECTOR_BANDS["DEFAULT"])
