*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim}      ${SCRIPTS}/healthcheck/test/pms_a1sim.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Deploy NONRTRIC without SDNC
    Start Process                   ${pms_a1sim}  >>  log_hc.txt    shell=yes
    ${cli_cmd_output}=              Wait For Process    timeout=1200
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
