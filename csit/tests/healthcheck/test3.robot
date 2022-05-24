*** Settings ***
Library           OperatingSystem
Library           Process

*** Variables ***

${pms_a1sim_sdnc}      ${SCRIPTS}/healthcheck/test/pms_a1sim_sdnc.sh
${pms_a1sim_sdnc_stdout}      pms_a1sim_sdnc_STDOUT.sh.txt
${pms_a1sim_sdnc_stderr}      pms_a1sim_sdnc_STDERR.sh.txt


*** Test Cases ***

Health check test case for NONRTRIC
    [Documentation]                Deploy NONRTRIC with SDNC
    ${cli_cmd_output}=              Run Process    ${pms_a1sim_sdnc}    shell=no    stdout=${pms_a1sim_sdnc_stdout}    stderr=${pms_a1sim_sdnc_stderr}
    Log                             Standard output: (${pms_a1sim_sdnc_stdout}) ${\n}${cli_cmd_output.stdout}
    Log                             Standard error: (${pms_a1sim_sdnc_stderr}) ${\n}${cli_cmd_output.stdout}
    Should Be Equal as Integers     ${cli_cmd_output.rc}    0
