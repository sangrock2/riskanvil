/**
 * LocalStorage 유틸리티
 * 안전한 읽기/쓰기 및 만료 처리
 */

const PREFIX = "stock-ai:";

/**
 * localStorage에 값 저장
 * @param {string} key - 키
 * @param {any} value - 저장할 값 (자동 JSON.stringify)
 * @param {number} ttl - Time to live (ms), 선택사항
 */
export function setItem(key, value, ttl = null) {
  try {
    const item = {
      value,
      timestamp: Date.now(),
      ttl,
    };
    localStorage.setItem(PREFIX + key, JSON.stringify(item));
    return true;
  } catch (error) {
    console.error("localStorage setItem error:", error);
    return false;
  }
}

/**
 * localStorage에서 값 읽기
 * @param {string} key - 키
 * @param {any} defaultValue - 기본값
 * @returns {any} 저장된 값 또는 기본값
 */
export function getItem(key, defaultValue = null) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    if (!raw) return defaultValue;

    const item = JSON.parse(raw);

    // TTL 체크
    if (item.ttl && Date.now() - item.timestamp > item.ttl) {
      removeItem(key);
      return defaultValue;
    }

    return item.value;
  } catch (error) {
    console.error("localStorage getItem error:", error);
    return defaultValue;
  }
}

/**
 * localStorage에서 값 삭제
 * @param {string} key - 키
 */
export function removeItem(key) {
  try {
    localStorage.removeItem(PREFIX + key);
    return true;
  } catch (error) {
    console.error("localStorage removeItem error:", error);
    return false;
  }
}

/**
 * 모든 stock-ai 관련 localStorage 항목 삭제
 */
export function clearAll() {
  try {
    const keys = Object.keys(localStorage);
    keys.forEach((key) => {
      if (key.startsWith(PREFIX)) {
        localStorage.removeItem(key);
      }
    });
    return true;
  } catch (error) {
    console.error("localStorage clearAll error:", error);
    return false;
  }
}

/**
 * 만료된 항목 제거
 */
export function cleanExpired() {
  try {
    const keys = Object.keys(localStorage);
    let cleaned = 0;

    keys.forEach((key) => {
      if (!key.startsWith(PREFIX)) return;

      try {
        const raw = localStorage.getItem(key);
        if (!raw) return;

        const item = JSON.parse(raw);
        if (item.ttl && Date.now() - item.timestamp > item.ttl) {
          localStorage.removeItem(key);
          cleaned++;
        }
      } catch {}
    });

    return cleaned;
  } catch (error) {
    console.error("localStorage cleanExpired error:", error);
    return 0;
  }
}

/**
 * 사용 중인 저장소 크기 확인 (대략적)
 * @returns {number} 바이트 단위 크기
 */
export function getStorageSize() {
  try {
    let size = 0;
    const keys = Object.keys(localStorage);

    keys.forEach((key) => {
      if (key.startsWith(PREFIX)) {
        const value = localStorage.getItem(key) || "";
        size += key.length + value.length;
      }
    });

    return size * 2; // UTF-16 (2 bytes per character)
  } catch {
    return 0;
  }
}
