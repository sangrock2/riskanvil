import { render, screen } from "@testing-library/react";
import App from "./App";

test("renders landing page for unauthenticated user", async () => {
  sessionStorage.removeItem("accessToken");
  render(<App />);
  expect(await screen.findByRole("link", { name: /login/i })).toBeInTheDocument();
});
