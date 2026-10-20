#!/usr/bin/env bash
# Version tags of Soukly (DEP-04): vMAJOR.MINOR.PATCH, read from the tags of the current repository.
#   version.sh last                     # last version tag (SemVer order), empty when there is none
#   version.sh next <patch|minor|major> # tag that follows it
# The first tag is v0.1.0 (v1.0.0 with major, kept for the opening to the public).
set -euo pipefail

last_tag() {
  git tag -l 'v*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true
}

case "${1:-}" in
  last)
    last_tag
    ;;
  next)
    bump="${2:-}"
    case "${bump}" in
      patch | minor | major) ;;
      *) echo "usage: version.sh next <patch|minor|major>" >&2; exit 2 ;;
    esac
    last="$(last_tag)"
    if [[ -z "${last}" ]]; then
      if [[ "${bump}" == major ]]; then echo v1.0.0; else echo v0.1.0; fi
      exit 0
    fi
    IFS=. read -r major minor patch <<< "${last#v}"
    case "${bump}" in
      major) echo "v$((major + 1)).0.0" ;;
      minor) echo "v${major}.$((minor + 1)).0" ;;
      patch) echo "v${major}.${minor}.$((patch + 1))" ;;
    esac
    ;;
  *)
    echo "usage: version.sh last | version.sh next <patch|minor|major>" >&2
    exit 2
    ;;
esac
