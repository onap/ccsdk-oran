*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim_sdnc}      ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh


*** Test Cases ***

Deploy NONRTRIC with SDNC
    [Documentation]                Deploy NONRTRIC with SDNC
    ${result_hc}=    Run Process   bash ${pms_a1sim_sdnc} >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0