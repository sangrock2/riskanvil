import { render, screen } from "@testing-library/react";
import App from "./App";

test("renders landing page for unauthenticated user", async () => {
  localStorage.removeItem("accessToken");
  render(<App />);
  expect(await screen.findByText(/Stock-AI/i)).toBeInTheDocument();
});
