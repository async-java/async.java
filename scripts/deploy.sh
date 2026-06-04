#!/usr/bin/env bash
set -euo pipefail

# Local Maven Central release helper.
#
# Required env vars:
#   CENTRAL_USERNAME
#   CENTRAL_PASSWORD
#   MAVEN_GPG_PASSPHRASE
#
# Required local state:
#   a GPG secret key available to `gpg`
#
# GitHub Actions normally handles releases from tags. Use this only when CI is
# unavailable and the Central Portal credentials are already configured.

if [ -z "${GPG_TTY:-}" ] && tty >/dev/null 2>&1; then
  export GPG_TTY
  GPG_TTY="$(tty)"
fi

mvn -s settings.xml -P publish-artifacts,release deploy "$@"
