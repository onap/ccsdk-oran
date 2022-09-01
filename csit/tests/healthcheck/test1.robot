*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${health_check}      ${SCRIPTS}/healthcheck/test/health_check.sh
${test1_stdout}      ${OUTPUT_DIR}/test1_healthcheck_STDOUT.log
${test1_stderr}      ${OUTPUT_DIR}/test1_healthcheck_STDERR.log

*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Health check - Test 1
    Log Variables
    ${cli_cmd_output}=              Run Process    ${health_check}    shell=no    stdout=${test1_stdout}    stderr=${test1_stderr}
    Log Many                        Standard output:     ${test1_stdout}    ${\n}    ${cli_cmd_output.stdout}
    Log Many                        Standard error:      ${test1_stderr}    ${\n}    ${cli_cmd_output.stderr}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
