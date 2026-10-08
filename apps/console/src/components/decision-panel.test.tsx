// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "../../messages/en.json";
import { DecisionPanel } from "./decision-panel";

function renderPanel(permissions: Parameters<typeof DecisionPanel>[0]["permissions"]) {
  const onApprove = vi.fn().mockResolvedValue({ status: "decided", state: "approved" });
  const onReturn = vi.fn().mockResolvedValue({ status: "decided", state: "returned" });
  render(
    <NextIntlClientProvider locale="en" messages={messages}>
      <DecisionPanel permissions={permissions} onApprove={onApprove} onReturn={onReturn} />
    </NextIntlClientProvider>,
  );
  return { onApprove, onReturn };
}

afterEach(cleanup);

describe("DecisionPanel", () => {
  it("disables both decisions for the proposer and says why (four-eyes rule)", () => {
    // GIVEN the API says the user proposed this change
    // WHEN the panel is shown
    const { onApprove } = renderPanel({ decide: false, reason: "own_request" });

    // THEN approve and return are disabled, the reason is explained, and clicking does nothing
    const approve = screen.getByRole("button", { name: "Approve" });
    expect(approve).toHaveProperty("disabled", true);
    expect(screen.getByRole("button", { name: "Return to proposer" })).toHaveProperty("disabled", true);
    expect(screen.getByText(/You proposed this change/)).toBeTruthy();
    fireEvent.click(approve);
    expect(onApprove).not.toHaveBeenCalled();
  });

  it("lets another approver approve, and requires a reason to return", () => {
    // GIVEN the API says the user may decide
    const { onApprove } = renderPanel({ decide: true });

    // WHEN nothing is typed THEN only approving is possible
    const returnButton = screen.getByRole("button", { name: "Return to proposer" });
    expect(returnButton).toHaveProperty("disabled", true);
    expect(screen.queryByText(/You proposed this change/)).toBeNull();

    // WHEN a reason is typed THEN returning is possible too
    fireEvent.change(screen.getByLabelText("Reason for returning"), { target: { value: "Photo is blurred" } });
    expect(returnButton).toHaveProperty("disabled", false);

    // WHEN approve is clicked THEN the action runs with an idempotency key
    fireEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(onApprove).toHaveBeenCalledWith(expect.stringMatching(/^[A-Za-z0-9_-]{8,}$/));
  });
});
