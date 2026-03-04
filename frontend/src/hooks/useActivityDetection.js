import { useEffect, useRef, useCallback } from "react";

/**
 * Detect user activity (mouse, keyboard, touch, scroll)
 * Used to determine if user is actively using the app
 *
 * @param {number} inactiveThreshold - Milliseconds of inactivity before considering user inactive (default: 5 minutes)
 * @returns {Object} - { isActive, lastActivityTime, markActive }
 */
export function useActivityDetection(inactiveThreshold = 5 * 60 * 1000) {
  const lastActivityTime = useRef(Date.now());
  const isActiveRef = useRef(true);

  const markActive = useCallback(() => {
    lastActivityTime.current = Date.now();
    isActiveRef.current = true;

    // Store in sessionStorage for cross-component access
    sessionStorage.setItem("lastActivity", String(Date.now()));
  }, []);

  useEffect(() => {
    const events = [
      "mousedown",
      "mousemove",
      "keypress",
      "scroll",
      "touchstart",
      "click",
    ];

    // Throttle activity tracking to avoid excessive updates
    let throttleTimeout = null;
    const throttledMarkActive = () => {
      if (!throttleTimeout) {
        throttleTimeout = setTimeout(() => {
          markActive();
          throttleTimeout = null;
        }, 1000); // Throttle to once per second
      }
    };

    // Add event listeners
    events.forEach((event) => {
      document.addEventListener(event, throttledMarkActive, { passive: true });
    });

    // Check inactivity periodically
    const inactivityCheck = setInterval(() => {
      const timeSinceLastActivity = Date.now() - lastActivityTime.current;
      const wasActive = isActiveRef.current;
      const nowActive = timeSinceLastActivity < inactiveThreshold;

      if (wasActive !== nowActive) {
        isActiveRef.current = nowActive;

        // Dispatch event for other components
        window.dispatchEvent(
          new CustomEvent("user:activity", {
            detail: { isActive: nowActive },
          })
        );
      }
    }, 30000); // Check every 30 seconds

    // Initialize
    markActive();

    return () => {
      events.forEach((event) => {
        document.removeEventListener(event, throttledMarkActive);
      });
      clearInterval(inactivityCheck);
      if (throttleTimeout) clearTimeout(throttleTimeout);
    };
  }, [inactiveThreshold, markActive]);

  return {
    isActive: isActiveRef.current,
    lastActivityTime: lastActivityTime.current,
    markActive,
  };
}

/**
 * Check if user was recently active
 * @param {number} threshold - Milliseconds (default: 5 minutes)
 * @returns {boolean} - True if user was active within threshold
 */
export function wasRecentlyActive(threshold = 5 * 60 * 1000) {
  const lastActivity = sessionStorage.getItem("lastActivity");
  if (!lastActivity) return false;

  const timeSinceLastActivity = Date.now() - parseInt(lastActivity, 10);
  return timeSinceLastActivity < threshold;
}
