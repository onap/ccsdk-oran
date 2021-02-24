*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${health_check}      ${SCRIPTS}/healthcheck/health_check.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Health check
    Start Process                   ${health_check}  >>  log_hc.txt    shell=yes
    ${cli_cmd_output}=              Wait For Process    timeout=600
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
