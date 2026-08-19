# Payment review — the smallest thing that shows a human in the loop, and a deadline on them.
#
# A person confirms whether the payment arrived. They get 30 seconds; if they do not answer,
# onTimeoutStepId routes the flow to the cancellation directly — no FORK, no TIMER, no
# second branch to keep in sync.
id: payment-review
name: Payment review
version: 1
description: A human confirms a payment, with a 30-second deadline that cancels the booking.
steps:
  - id: start
    type: START
    name: Start

  # Deliberately NOT routed to the forms engine: with no topic this goes to `downstream`, and the
  # test worker answers it from TEST_CONFIG like any other task. That is what makes the 30-second
  # deadline testable — `{"tasks":{"verify-payment":{"outcome":"NO_REPLY"}}}` is a person who never
  # answered, on demand, which is a state no real reviewer can be asked to produce.
  # Add `topic: forms` to make it a real human task instead.
  - id: verify-payment
    type: USER_TASK
    name: Verify payment received
    formId: verify-payment
    preconditionStepId: start
    timeout: PT30S
    onTimeoutStepId: cancel-booking

  - id: confirm-booking
    type: ACTION
    name: Confirm booking
    preconditions:
      - stepId: verify-payment
        expression: "paymentReceived == 'true'"

  # Reached by an explicit rejection, or by the timeout above.
  - id: cancel-booking
    type: ACTION
    name: Cancel booking
    preconditions:
      - stepId: verify-payment
        expression: "paymentReceived == 'false'"

  - id: decided
    type: JOIN
    name: Decided
    joinType: XOR
    preconditionStepIds: [confirm-booking, cancel-booking]

  - id: end
    type: END
    name: End
    preconditionStepId: decided
