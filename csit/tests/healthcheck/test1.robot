*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${health_check}      ${SCRIPTS}/healthcheck/test/health_check.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Health check
    Start Process                   ${health_check}     shell=yes
    ${cli_cmd_output}=              Wait For Process    timeout=600
    Log                             ${cli_cmd_output.stdout}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
