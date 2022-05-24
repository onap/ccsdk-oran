*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim_sdnc}             ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh
${pms_a1sim_sdnc_stdout}      ${WORKSPACE}/pms_a1sim_sdnc_STDOUT.sh.txt
${pms_a1sim_sdnc_stderr}      ${WORKSPACE}/pms_a1sim_sdnc_STDERR.sh.txt

*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                 Deploy NONRTRIC with SDNC
    Log                             Variables
    ${cli_cmd_output}=              Run Process    ${pms_a1sim_sdnc}    shell=no    stdout=${pms_a1sim_sdnc_stdout}    stderr=${pms_a1sim_sdnc_stderr}
    Log Many                        Standard output:     ${pms_a1sim_sdnc_stdout}    ${\n}    ${cli_cmd_output.stdout}
    Log Many                        Standard error:      ${pms_a1sim_sdnc_stderr}    ${\n}    ${cli_cmd_output.stderr}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
