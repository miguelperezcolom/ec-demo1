# Parallel notifications — a FORK/JOIN·AND barrier and nothing else.
#
# Here to make the fan-out visible on its own in the definition viewer's graph, without the
# saga and the human task of order-fulfilment on top of it.
id: notify-parallel
name: Parallel notifications
version: 1
description: Fan out to three channels at once and wait for all of them.
steps:
  - id: start
    type: START
    name: Start

  - id: fanout
    type: FORK
    name: Notify all channels
    preconditionStepId: start

  - id: email
    type: ACTION
    name: Send email
    preconditionStepId: fanout

  - id: sms
    type: ACTION
    name: Send SMS
    preconditionStepId: fanout

  - id: push
    type: ACTION
    name: Send push notification
    preconditionStepId: fanout

  - id: all-sent
    type: JOIN
    name: All notifications sent
    joinType: AND
    preconditionStepIds: [email, sms, push]

  - id: end
    type: END
    name: Done
    preconditionStepId: all-sent
