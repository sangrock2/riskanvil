import { describe, expect, test } from "vitest";
import { getTranslation } from "../i18n/translations";
import { toUserErrorMessage } from "./errorMessage";

const tKo = (key, params) => getTranslation("ko", key, params);

describe("toUserErrorMessage", () => {
  test("maps watchlist duplicate errors to a friendly message", () => {
    const error = {
      status: 400,
      path: "/api/watchlist",
      apiError: {
        status: 400,
        error: "illegal_state",
        message: "already exists",
        path: "/api/watchlist",
      },
      message: "already exists",
    };

    expect(toUserErrorMessage(error, tKo, "watchlist.addError", "watchlistAdd"))
      .toBe("이미 관심종목에 추가된 종목입니다.");
  });

  test("maps invalid credential errors to a friendly message", () => {
    const error = {
      status: 400,
      apiError: {
        status: 400,
        message: "invalid credentials",
      },
      message: "invalid credentials",
    };

    expect(toUserErrorMessage(error, tKo, "auth.loginRequired", "login"))
      .toBe("이메일 또는 비밀번호가 올바르지 않습니다.");
  });
});
