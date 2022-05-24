*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim_sdnc}    ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh
${test3_stdout}      ${OUTPUT_DIR}/test3_pms_a1sim_sdnc_STDOUT.sh.log
${test3_stderr}      ${OUTPUT_DIR}/test3_pms_a1sim_sdnc_STDERR.sh.log

*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                 Deploy NONRTRIC with SDNC - Test 3
    Log Variables
    ${cli_cmd_output}=              Run Process    ${pms_a1sim_sdnc}    shell=no    stdout=${test3_stdout}    stderr=${test3_stderr}
    Log Many                        Standard output:     ${test3_stdout}    ${\n}    ${cli_cmd_output.stdout}
    Log Many                        Standard error:      ${test3_stderr}    ${\n}    ${cli_cmd_output.stderr}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
