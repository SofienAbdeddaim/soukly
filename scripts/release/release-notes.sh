#!/usr/bin/env bash
# Draft release notes of Soukly (DEP-04), in Markdown, from the history of main between two refs.
#   release-notes.sh <from tag or ""> <to sha> [untested]
# One line per change of main (first parent): the title of the merged pull request, or the subject
# of a commit made on main, in the project's format "type(TICKET): summary".
#   feat -> New features, fix -> Fixes, chore and docs -> left out, anything else -> Other.
# "untested" adds a warning at the top: the version was never deployed successfully in dev.
set -euo pipefail

from="${1:-}"
to="${2:?usage: release-notes.sh <from tag or \"\"> <to sha> [untested]}"
untested="${3:-}"
repo_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-SofienAbdeddaim/soukly}"

features=()
fixes=()
others=()
range="${to}"
if [[ -n "${from}" ]]; then range="${from}..${to}"; fi

while IFS= read -r -d $'\x1e' record; do
  record="${record#$'\n'}"
  [[ -n "${record}" ]] || continue
  subject="${record%%$'\x1f'*}"
  body="${record#*$'\x1f'}"
  pr=""
  if [[ "${subject}" =~ ^Merge\ pull\ request\ \#([0-9]+)\  ]]; then
    # GitHub merge commit: the pull request title is the first line of the body.
    pr="${BASH_REMATCH[1]}"
    title="$(sed -n '/[^[:space:]]/{p;q}' <<< "${body}")"
  elif [[ "${subject}" =~ ^(.*)\ \(\#([0-9]+)\)$ ]]; then
    # Squash merge: "title (#12)".
    title="${BASH_REMATCH[1]}"
    pr="${BASH_REMATCH[2]}"
  else
    title="${subject}"
  fi
  # Other merges (for example "Merge branch 'main' into ...") carry no change of their own.
  [[ -n "${title}" && "${title}" != Merge\ * ]] || continue

  type=other
  ticket=""
  summary="${title}"
  if [[ "${title}" =~ ^([a-z]+)(\(([^\)]+)\))?!?:\ (.+)$ ]]; then
    type="${BASH_REMATCH[1]}"
    ticket="${BASH_REMATCH[3]}"
    summary="${BASH_REMATCH[4]}"
  fi
  line="- ${ticket:+**${ticket}**: }${summary}${pr:+ ([#${pr}](${repo_url}/pull/${pr}))}"
  case "${type}" in
    feat) features+=("${line}") ;;
    fix) fixes+=("${line}") ;;
    chore | docs) ;;
    *) others+=("${line}") ;;
  esac
done < <(git log --first-parent --format='%s%x1f%b%x1e' "${range}")

if [[ "${untested}" == untested ]]; then
  echo "> **Not tested in dev**: deployed with \`allow_untested\` (urgent fix)."
  echo
fi
section() {
  local heading="$1"
  shift
  (($# > 0)) || return 0
  printf '### %s\n\n' "${heading}"
  printf '%s\n' "$@"
  echo
}
section "New features" "${features[@]}"
section "Fixes" "${fixes[@]}"
section "Other" "${others[@]}"
if ((${#features[@]} + ${#fixes[@]} + ${#others[@]} == 0)); then
  echo "No change worth noting (chore and docs commits only)."
fi
