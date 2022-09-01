*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim}         ${SCRIPTS}/healthcheck/test/pms_a1sim.sh
${test2_stdout}      ${OUTPUT_DIR}/test2_pms_a1sim_STDOUT.log
${test2_stderr}      ${OUTPUT_DIR}/test2_pms_a1sim_STDERR.log

*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                 Deploy NONRTRIC without SDNC - Test 2
    Log Variables
    ${cli_cmd_output}=              Run Process    ${pms_a1sim}    shell=no    stdout=${test2_stdout}    stderr=${test2_stderr}
    Log Many                        Standard output:     ${test2_stdout}    ${\n}    ${cli_cmd_output.stdout}
    Log Many                        Standard error:      ${test2_stderr}    ${\n}    ${cli_cmd_output.stderr}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
