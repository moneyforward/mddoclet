#!/usr/bin/env bash

set -E -o nounset -o errexit +o posix -o pipefail
shopt -s inherit_errexit

function debug_javadoc() {
  echo "Run 'MdDoclet_debug' from IDEA's with debug mode, then you will get a debugger for MdDoclet" >&2
  javadoc -J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 "${@}"
}
function run_javadoc() {
  javadoc "${@}"
}

function main() {
  local _arg="${1:-none}"
  if [[ "${_arg}" == "--debug" ]]; then
    _func="debug_javadoc"
    shift
  elif [[ "${_arg}" == "--run" ]]; then
    _func="run_javadoc"
    shift
  else
    _func="run_javadoc"
  fi
  "${_func}" @src/build_tools/options @src/build_tools/packages "${@}"
}

main "${@}"
