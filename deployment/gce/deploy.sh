#!/usr/bin/env bash
#
# Deploys a CareFlow image on the GCE VM with an automatic health-gated
# rollback.
#
#   deploy.sh <image-uri>
#
# The currently running image is recorded before the swap. If the new container
# fails to become healthy within the timeout, the previous image is restored and
# the script exits non-zero — so a failed deploy never leaves the VM serving a
# broken build, and CI sees the failure.

set -euo pipefail

IMAGE_URI="${1:-}"
APP_DIR="/opt/onedose/one_dose_backend"
ENV_FILE="${APP_DIR}/.env"
CONTAINER_NAME="careflow-backend"
PREVIOUS_IMAGE_FILE="${APP_DIR}/deployment/previous-image"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
# Spring Boot needs roughly four and a half minutes to reach UP on the e2-micro
# this runs on, so the old 120s budget failed every deploy while the app was
# still starting normally. Six minutes leaves headroom without masking a
# genuinely stuck boot.
HEALTH_TIMEOUT_SECONDS=360
HEALTH_INTERVAL_SECONDS=5
# The backend resolves MySQL by container name, so the deployed container
# must join the same user-defined bridge the compose stack created. On the
# default bridge the "mysql" hostname does not resolve, so the application
# cannot reach its database and every deploy fails its health check.
DOCKER_NETWORK="${DOCKER_NETWORK:-deployment_careflow}"
# The MySQL container's name on that network, and the port it listens on
# inside it — not the host-side mapping, which compose deliberately omits.
DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"

log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }
fail() { log "ERROR: $*"; exit 1; }

[[ -n "${IMAGE_URI}" ]] || fail "Usage: deploy.sh <image-uri>"
[[ -f "${ENV_FILE}" ]] || fail "Environment file ${ENV_FILE} is missing."

# Waits for the application to report healthy, returning non-zero on timeout.
wait_for_health() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
    while (( SECONDS < deadline )); do
        if curl -fsS --max-time 5 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        if ! docker ps --filter "name=^${CONTAINER_NAME}$" --format '{{.Names}}' | grep -q .; then
            log "Container exited during startup. Recent logs:"
            docker logs --tail 50 "${CONTAINER_NAME}" 2>&1 || true
            return 1
        fi
        sleep "${HEALTH_INTERVAL_SECONDS}"
    done
    log "Health check did not pass within ${HEALTH_TIMEOUT_SECONDS}s. Recent logs:"
    docker logs --tail 50 "${CONTAINER_NAME}" 2>&1 || true
    return 1
}

start_container() {
    local image="$1"
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    # Published on loopback only — the public entry point is Nginx on 443.
    # DB_HOST and DB_PORT are set by docker-compose as service-level values
    # rather than living in the env file, so --env-file alone leaves them
    # unset and the JDBC URL collapses to an unreachable host. They are passed
    # explicitly here, after the env file so a value in the file still wins.
    docker run -d \
        --name "${CONTAINER_NAME}" \
        --restart unless-stopped \
        --env-file "${ENV_FILE}" \
        --env "DB_HOST=${DB_HOST}" \
        --env "DB_PORT=${DB_PORT}" \
        --network "${DOCKER_NETWORK}" \
        --publish 127.0.0.1:8080:8080 \
        --log-driver json-file \
        --log-opt max-size=20m \
        --log-opt max-file=5 \
        "${image}" >/dev/null
}

CURRENT_IMAGE="$(docker inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}" 2>/dev/null || echo '')"
if [[ -n "${CURRENT_IMAGE}" ]]; then
    log "Currently running: ${CURRENT_IMAGE}"
    echo "${CURRENT_IMAGE}" > "${PREVIOUS_IMAGE_FILE}"
else
    log "No existing container found; this is a first deployment."
fi

log "Pulling ${IMAGE_URI}"
docker pull "${IMAGE_URI}" >/dev/null || fail "Failed to pull ${IMAGE_URI}"

log "Starting the new container"
start_container "${IMAGE_URI}"

log "Waiting for the application to report healthy"
if wait_for_health; then
    log "Health check passed. Deployment of ${IMAGE_URI} succeeded."
    docker image prune -f --filter "until=168h" >/dev/null 2>&1 || true
    exit 0
fi

log "Health check FAILED — rolling back."

if [[ -n "${CURRENT_IMAGE}" ]]; then
    start_container "${CURRENT_IMAGE}"
    if wait_for_health; then
        log "Rollback to ${CURRENT_IMAGE} succeeded. The previous version is serving traffic."
    else
        log "CRITICAL: rollback to ${CURRENT_IMAGE} also failed to become healthy."
    fi
else
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    log "No previous image to roll back to; the container has been removed."
fi

fail "Deployment of ${IMAGE_URI} failed its health check."
