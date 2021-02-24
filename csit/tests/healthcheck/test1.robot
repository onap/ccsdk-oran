*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${health_check}      ${SCRIPTS}/healthcheck/test/health_check.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Health check
    ${result_hc}=    Run Process   bash ${health_check} >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0
