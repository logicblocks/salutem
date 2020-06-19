#!/usr/bin/env bash

[ -n "$DEBUG" ] && set -x
set -e
set -o pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SCRIPT_DIR/../../.." && pwd )"

cd "$PROJECT_DIR"

mkdir -p ~/.lein
cp config/secrets/clojars/credentials.clj.gpg ~/.lein/credentials.clj.gpg
chmod 0600 ~/.lein/credentials.clj.gpg
