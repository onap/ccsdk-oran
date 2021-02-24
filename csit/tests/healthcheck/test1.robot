*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${health_check}    ${SCRIPTS}/healthcheck/test/health_check.sh
${pms_a1sim}    ${SCRIPTS}/healthcheck/test/pms_a1sim.sh
${pms_a1sim_sdnc}    ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh


*** Test Cases ***
Health check test case for NONRTRIC
    [Documentation]   Health check
    ${result_hc}=    Run Process   bash ${health_check} >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0

Functional Test Case 1
    [Documentation]   Deploy PMS without SDNC
    ${result_hc}=    Run Process   bash ${pms_a1sim}  >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0

Functional Test Case 2
    [Documentation]   Deploy PMS with SDNC
    ${result_hc}=    Run Process   bash ${pms_a1sim_sdnc}  >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0
