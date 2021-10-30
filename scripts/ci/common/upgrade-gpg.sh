#!/usr/bin/env bash

[ -n "$DEBUG" ] && set -x
set -e
set -o pipefail

apt update
apt -y --no-install-recommends install gpg
