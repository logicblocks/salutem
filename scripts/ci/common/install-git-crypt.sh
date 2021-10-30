#!/usr/bin/env bash

[ -n "$DEBUG" ] && set -x
set -e
set -o pipefail

sudo apt-get install libssl1.0.0
apt-get update
apt-get install -y --no-install-recommends \
  git \
  ssh \
  libssl1.1 \
  git-crypt
