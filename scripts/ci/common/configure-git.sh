#!/usr/bin/env bash

[ -n "$DEBUG" ] && set -x
set -e
set -o pipefail

git crypt unlock

KEY_UID="$(cat config/secrets/ci/gpg.uid)"
KEY_ID="$(gpg --list-keys --with-colons | \
  grep -C 1 "${KEY_UID}" | \
  head -n 1 | \
  cut -d ':' -f 10)"

git config --global user.email "circleci@infrablocks.io"
git config --global user.name "Circle CI"
git config --global user.signingkey "${KEY_ID}"
