# Order fulfilment — the flagship demo process.
#
# Exercises, in one definition, everything the engine is for: retries and timeouts on the
# unreliable steps, a FORK/JOIN·AND barrier for the two things that can happen at once, a
# human decision that routes the flow, a JOIN·XOR that ends on whichever branch wins, and
# saga compensation for the two steps that took money and stock.
#
# Every ACTION is answered by the test worker, which does no work: the process states what
# each task should do in its TEST_CONFIG variable and the worker plays it back. That is how
# this demo produces a failure — and therefore a rollback — on demand.
id: order-fulfilment
name: Order fulfilment
version: 1
description: >-
  Validate an order, reserve stock and charge the card in parallel, let a human decide on
  shipping, and undo both money and stock if anything downstream fails.
steps:

  - id: start
    type: START
    name: Order received

  # Fast and idempotent, but it is the front door: give it a deadline and two retries so a
  # transient blip does not fail the order.
  - id: validate-order
    type: ACTION
    name: Validate order
    topic: order-validator
    preconditionStepId: start
    timeout: PT30S
    retries: 2

  # Stock and money are independent — run them together rather than in sequence.
  - id: fanout
    type: FORK
    name: Reserve and charge
    preconditionStepId: validate-order

  - id: reserve-stock
    type: ACTION
    name: Reserve stock
    topic: inventory
    preconditionStepId: fanout
    timeout: PT1M
    retries: 1
    compensable: true
    compensationStepId: release-stock

  - id: charge-card
    type: ACTION
    name: Charge card
    topic: payments
    preconditionStepId: fanout
    timeout: PT30S
    retries: 1
    compensable: true
    compensationStepId: refund-card

  # AND: shipping is not decided until both branches are in.
  - id: reserved-and-charged
    type: JOIN
    name: Reserved and charged
    joinType: AND
    preconditionStepIds: [reserve-stock, charge-card]

  # The human decision. Ten minutes to answer; if nobody does, the order ships standard
  # rather than sitting there — onTimeoutStepId routes that natively, with no timer branch.
  - id: review-shipping
    type: USER_TASK
    name: Review shipping
    formId: shipping-review
    preconditionStepId: reserved-and-charged
    timeout: PT10M
    onTimeoutStepId: ship-order

  # The two outcomes of the review. Guards live on the incoming link, so both read the same
  # `approved` variable the form wrote and exactly one of them runs.
  - id: ship-order
    type: ACTION
    name: Ship order
    topic: shipping
    preconditions:
      - stepId: review-shipping
        expression: "approved == 'true'"

  - id: cancel-order
    type: ACTION
    name: Cancel order
    topic: shipping
    preconditions:
      - stepId: review-shipping
        expression: "approved == 'false'"

  # XOR: whichever outcome completes first ends the process; END cancels the loser.
  - id: outcome
    type: JOIN
    name: Outcome decided
    joinType: XOR
    preconditionStepIds: [ship-order, cancel-order]

  - id: notify-customer
    type: ACTION
    name: Notify customer
    topic: notifications
    preconditionStepId: outcome

  - id: end
    type: END
    name: Order closed
    preconditionStepId: notify-customer

  # ── Compensations ───────────────────────────────────────────────────────────
  # Reached only by the saga rollback, in reverse execution order. They are wired to `start`
  # with a guard that is always false so they are part of the graph without ever running
  # forward — the idiom the engine expects for a compensation step.
  - id: release-stock
    type: ACTION
    name: Release stock
    topic: inventory
    preconditionStepId: start
    preconditionExpression: "false"

  - id: refund-card
    type: ACTION
    name: Refund card
    topic: payments
    preconditionStepId: start
    preconditionExpression: "false"
