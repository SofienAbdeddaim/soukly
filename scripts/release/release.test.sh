#!/usr/bin/env bash
# Tests of version.sh and release-notes.sh (DEP-04) on a throwaway git repository.
#   bash scripts/release/release.test.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
cd "${WORK}" || exit 1
git init -q -b main .
git config user.email test@example.test
git config user.name test
export GITHUB_SERVER_URL=https://github.com GITHUB_REPOSITORY=owner/soukly

failures=0
expect() { # expect <name> <expected> <actual>
  if [[ "$3" == "$2" ]]; then
    echo "ok   $1"
  else
    printf 'FAIL %s\n     expected: %s\n     actual:   %s\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}
contains() { # contains <name> <text> <needle>
  if [[ "$2" == *"$3"* ]]; then echo "ok   $1"; else
    printf 'FAIL %s\n     missing: %s\n' "$1" "$3"
    failures=$((failures + 1))
  fi
}
absent() { # absent <name> <text> <needle>
  if [[ "$2" != *"$3"* ]]; then echo "ok   $1"; else
    printf 'FAIL %s\n     unexpected: %s\n' "$1" "$3"
    failures=$((failures + 1))
  fi
}
commit() { git commit -q --allow-empty -m "$1"; }
version() { bash "${HERE}/version.sh" "$@"; }
notes() { bash "${HERE}/release-notes.sh" "$@"; }

# ---- Versions ----
commit "chore(SETUP-01): initial commit"
expect "no tag: last is empty" "" "$(version last)"
expect "no tag: first patch is v0.1.0" v0.1.0 "$(version next patch)"
expect "no tag: first minor is v0.1.0" v0.1.0 "$(version next minor)"
expect "no tag: major gives v1.0.0" v1.0.0 "$(version next major)"
git tag v1.2.0
git tag v1.2 # not a version tag
git tag release-9.9.9
expect "v1.2.0 + patch" v1.2.1 "$(version next patch)"
expect "v1.2.0 + minor" v1.3.0 "$(version next minor)"
expect "v1.2.0 + major" v2.0.0 "$(version next major)"
git tag v1.9.0
git tag v1.10.0
expect "SemVer order: v1.10.0 is after v1.9.0" v1.10.0 "$(version last)"
expect "v1.10.0 + patch" v1.10.1 "$(version next patch)"
version next huge > /dev/null 2>&1
expect "unknown bump refused" 2 "$?"

# ---- Release notes ----
git tag -d v1.9.0 v1.10.0 > /dev/null
base=$(git rev-parse HEAD)
# A pull request merged with a merge commit: its title is the first line of the body.
git checkout -q -b feat/ANN-05
commit "feat(ANN-05): add the list step"
commit "fix(ANN-05): review comments"
git checkout -q main
git merge -q --no-ff feat/ANN-05 -m "Merge pull request #21 from owner/feat/ANN-05" -m "feat(ANN-05): my listings"
# Squash merges and commits made on main.
commit "fix(CATA-03): Arabic prices (#22)"
commit "docs(DEP-04): explain the release"
commit "chore(deps): bump something (#23)"
commit "refactor(ANN-01): simpler form"
commit "Merge branch 'main' into feat/x"
out="$(notes v1.2.0 HEAD)"
contains "feat in New features with ticket and PR link" "${out}" $'### New features\n\n- **ANN-05**: my listings ([#21](https://github.com/owner/soukly/pull/21))'
contains "fix in Fixes" "${out}" $'### Fixes\n\n- **CATA-03**: Arabic prices ([#22](https://github.com/owner/soukly/pull/22))'
contains "other types in Other" "${out}" $'### Other\n\n- **ANN-01**: simpler form'
absent "commits inside a merged PR not listed" "${out}" "add the list step"
absent "docs left out" "${out}" "explain the release"
absent "chore left out" "${out}" "bump something"
absent "plain merges left out" "${out}" "Merge branch"
absent "no warning when tested" "${out}" "Not tested in dev"
out="$(notes v1.2.0 HEAD untested)"
contains "untested warning first" "${out%%$'\n'*}" "Not tested in dev"
out="$(notes "${base}" "${base}")"
contains "nothing to note" "${out}" "No change worth noting"
git tag v1.3.0
commit "feat(SOC-01): connect Facebook"
out="$(notes v1.3.0 HEAD)"
expect "only the commits since the last tag" $'### New features\n\n- **SOC-01**: connect Facebook' "${out}"
out="$(notes "" "${base}")"
absent "no tag yet: whole history, chore left out" "${out}" "initial commit"

echo
if ((failures > 0)); then
  echo "${failures} check(s) failed"
  exit 1
fi
echo "all checks passed"
