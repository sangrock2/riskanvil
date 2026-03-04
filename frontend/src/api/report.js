import { apiFetch } from "./http";

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

function isoDate() {
  return new Date().toISOString().split("T")[0];
}

export async function exportReportTxt(payload) {
  const blob = await apiFetch("/api/market/report/export/txt", {
    method: "POST",
    body: JSON.stringify(payload),
    responseType: "blob",
  });
  downloadBlob(blob, `report_${payload.ticker}_${isoDate()}.txt`);
}

export async function exportReportPdf(payload) {
  const blob = await apiFetch("/api/market/report/export/pdf", {
    method: "POST",
    body: JSON.stringify(payload),
    responseType: "blob",
  });
  downloadBlob(blob, `report_${payload.ticker}_${isoDate()}.pdf`);
}

export async function exportReportJson(payload) {
  const data = await apiFetch("/api/market/report/export/json", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  downloadBlob(blob, `report_${payload.ticker}_${isoDate()}.json`);
}

