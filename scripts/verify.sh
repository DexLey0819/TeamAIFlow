#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/teamflow-ai-frontend"
BACKEND_DIR="$ROOT_DIR/teamflow-ai-backend"

echo "==> Frontend checks"
cd "$FRONTEND_DIR"
if [ ! -d node_modules ]; then
  echo "Frontend dependencies are missing. Run: cd teamflow-ai-frontend && npm ci"
  exit 1
fi
npm run check

echo "==> Frontend build"
npm run build

echo "==> Backend architecture checks"
cd "$ROOT_DIR"
node scripts/check-ai-service-modularization.mjs
node scripts/check-agent-profile-foundation.mjs

echo "==> Backend tests"
if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  echo "Setting JAVA_HOME to $JAVA_HOME"
fi

if command -v mvn >/dev/null 2>&1; then
  cd "$BACKEND_DIR"
  mvn test
else
  echo "WARN: mvn was not found; skipped backend tests."
  echo "Install Maven 3.8+ or run backend tests in CI."
fi
