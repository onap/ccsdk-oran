*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim}      ${SCRIPTS}/healthcheck/test/pms_a1sim.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Deploy NONRTRIC without SDNC
    Start Process                   ${pms_a1sim}        shell=yes
    ${cli_cmd_output}=              Wait For Process    timeout=600
    Log                             ${cli_cmd_output.stdin}
    Log                             ${cli_cmd_output.stdout}
    Log                             ${cli_cmd_output.stderr}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
