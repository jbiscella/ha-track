Feature: Series defensive immutability
  Maps commons/CLAUDE.md section 3 (Series types).

  Scenario: OHLCSeries defensively copies its bar list
    Given a mutable list of 2 OHLC bars
    And an OHLCSeries built from that list
    When I clear the original list
    Then the OHLCSeries still has 2 bars

  Scenario: HASeries defensively copies its bar list
    Given a mutable list of 2 HA bars
    And an HASeries built from that list
    When I clear the original list
    Then the HASeries still has 2 bars

  Scenario: OHLCSeries rejects a null bar list
    When I construct an OHLCSeries with a null bar list
    Then a NullPointerException is thrown

  Scenario: HASeries rejects a null bar list
    When I construct an HASeries with a null bar list
    Then a NullPointerException is thrown

  Scenario: OHLCSeries rejects a list containing a null bar
    When I construct an OHLCSeries from a list containing a null bar
    Then a NullPointerException is thrown

  Scenario: HASeries rejects a list containing a null bar
    When I construct an HASeries from a list containing a null bar
    Then a NullPointerException is thrown
