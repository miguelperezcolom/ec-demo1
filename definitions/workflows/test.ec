name: New Workflow
steps:
  - id: step-rpgm97
    type: START
    name: New Start
  - id: step-5y1g2o
    type: ACTION
    name: New Action
    preconditions:
      - stepId: step-rpgm97
  - id: step-cdhbbe
    type: END
    name: New End
    preconditions:
      - stepId: step-5y1g2o
