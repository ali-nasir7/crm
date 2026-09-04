# CI workflows

GitHub Apps in this workspace cannot push workflow definitions. To enable CI, copy these two files:

    mkdir -p .github/workflows
    cp docs/ci/backend-ci.yml .github/workflows/
    cp docs/ci/frontend-ci.yml .github/workflows/

backend-ci.yml — JDK 21, Maven build + unit tests, plus a Docker-enabled job running the
Testcontainers integration tests against real PostgreSQL (`-Dsurefire.excludedGroups=`).
frontend-ci.yml — `npm ci` + type-check + production build.
