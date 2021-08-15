#!/usr/bin/env bash

[ -n "$DEBUG" ] && set -x
set -e
set -o pipefail

echo "deb http://deb.debian.org/debian unstable main" >> /etc/apt/sources.list
echo "deb http://deb.debian.org/debian experimental main" >> /etc/apt/sources.list

apt update
apt -t experimental -y --no-install-recommends install gpg
