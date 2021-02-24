*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim}         ${SCRIPTS}/healthcheck/test/pms_a1sim.sh
${pms_a1sim_sdnc}    ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh


*** Test Cases ***


Functional Test Case without SDNC
    [Documentation]                Deploy PMS without SDNC
    ${result_hc}=    Run Process   bash ${pms_a1sim}  >> log_hc.txt    shell=yes
    Should Be Equal As Integers    ${result_hc.rc}    0