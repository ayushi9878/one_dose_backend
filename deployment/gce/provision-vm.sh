#!/usr/bin/env bash
#
# Provisions the CareFlow GCE VM: Docker, Nginx, firewall and the application
# directory. Run once per VM as a sudo-capable user.
#
#   curl -fsSL <raw-url>/provision-vm.sh | sudo bash -s -- careflow.example.com

set -euo pipefail

CAREFLOW_DOMAIN="${1:-}"
APP_DIR="/opt/careflow"
APP_USER="careflow"

if [[ -z "${CAREFLOW_DOMAIN}" ]]; then
    echo "Usage: provision-vm.sh <domain>" >&2
    exit 1
fi

echo "==> Updating base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq ca-certificates curl gnupg lsb-release ufw nginx

echo "==> Installing Docker Engine"
install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
fi
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

echo "==> Creating the application account and directory"
if ! id -u "${APP_USER}" >/dev/null 2>&1; then
    useradd --system --create-home --home-dir "${APP_DIR}" --shell /usr/sbin/nologin "${APP_USER}"
fi
usermod -aG docker "${APP_USER}"
mkdir -p "${APP_DIR}"/{config,logs,backups}
chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"
chmod 750 "${APP_DIR}"

echo "==> Configuring the firewall"
# Only SSH and HTTP/HTTPS are reachable; port 8080 stays bound to loopback and
# is never opened, so Spring Boot is unreachable except through Nginx.
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> Installing the Nginx configuration"
mkdir -p /etc/nginx/snippets /var/www/certbot
install -m 0644 "$(dirname "$0")/../nginx/careflow-proxy.conf" \
    /etc/nginx/snippets/careflow-proxy.conf 2>/dev/null || true

if [[ -f "$(dirname "$0")/../nginx/careflow.conf" ]]; then
    sed "s/\${CAREFLOW_DOMAIN}/${CAREFLOW_DOMAIN}/g" \
        "$(dirname "$0")/../nginx/careflow.conf" > /etc/nginx/sites-available/careflow.conf
    ln -sf /etc/nginx/sites-available/careflow.conf /etc/nginx/sites-enabled/careflow.conf
    rm -f /etc/nginx/sites-enabled/default
fi

echo "==> Installing Certbot for TLS"
apt-get install -y -qq certbot python3-certbot-nginx

echo "==> Enabling log rotation for container logs"
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  }
}
JSON
systemctl restart docker

echo "==> Installing the systemd unit"
cat > /etc/systemd/system/careflow.service <<UNIT
[Unit]
Description=CareFlow backend
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/docker start careflow-backend
ExecStop=/usr/bin/docker stop careflow-backend

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable careflow.service

cat <<NEXT

Provisioning complete.

Remaining manual steps:
  1. Write ${APP_DIR}/config/careflow.env with the production environment
     variables (see .env.example). Restrict it:
         chown ${APP_USER}:${APP_USER} ${APP_DIR}/config/careflow.env
         chmod 600 ${APP_DIR}/config/careflow.env
  2. Issue the TLS certificate:
         certbot --nginx -d ${CAREFLOW_DOMAIN}
  3. Run deploy.sh to pull and start the first image.

Spring Boot is never exposed publicly: it binds to 127.0.0.1:8080 and only
ports 22, 80 and 443 are open on the firewall.
NEXT
