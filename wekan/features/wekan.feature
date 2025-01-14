Feature: WeKan Deployment

  Scenario: WeKan pods are running
    Given the WeKan Helm chart is deployed
    Then the WeKan pods should be running