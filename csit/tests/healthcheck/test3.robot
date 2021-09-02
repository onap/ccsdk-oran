*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim_sdnc}      ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Deploy NONRTRIC with SDNC
    Start Process                   ${pms_a1sim_sdnc}  >>  log_hc.txt    shell=yes
    ${cli_cmd_output}=              Wait For Process    timeout=600
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
