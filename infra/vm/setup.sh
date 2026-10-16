#!/usr/bin/env bash
# Prepares the Oracle VM (Ubuntu 24.04, arm64) for Soukly (DEP-02). Idempotent: running it again
# changes nothing that is already in place and duplicates nothing.
#
#   sudo bash infra/vm/setup.sh --deploy-key /path/to/deploy.pub   # first run
#   sudo bash infra/vm/setup.sh                                    # later runs keep the existing key
#
# What it does: system updates and unattended security upgrades, Docker Engine and Compose from
# Docker's apt repository, Docker log rotation, host firewall (22, 80, 443), SSH keys only,
# fail2ban, the "deploy" user for GitHub Actions, /opt/soukly, the Docker networks shared with the
# edge Caddy, the nightly prod backup timer, and the Africa/Tunis time zone.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_INFRA="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEPLOY_USER=deploy
SOUKLY_ROOT=/opt/soukly
DEPLOY_KEY_FILE=""

log() { printf '\n[setup] %s\n' "$*"; }
die() { printf '[setup] ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-key) DEPLOY_KEY_FILE="${2:-}"; shift 2 ;;
    -h | --help) sed -n '2,12p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ $EUID -eq 0 ]] || die "run with sudo"
# shellcheck source=/dev/null
. /etc/os-release
[[ "${ID}" == ubuntu ]] || die "Ubuntu expected, found ${ID}"
[[ "${VERSION_ID}" == 24.04 ]] || log "warning: written for Ubuntu 24.04, found ${VERSION_ID}"
if [[ -n "${DEPLOY_KEY_FILE}" ]]; then
  [[ -r "${DEPLOY_KEY_FILE}" ]] || die "cannot read ${DEPLOY_KEY_FILE}"
  grep -Eq '^(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa) ' "${DEPLOY_KEY_FILE}" \
    || die "${DEPLOY_KEY_FILE} is not an SSH public key"
fi

export DEBIAN_FRONTEND=noninteractive

# Writes stdin to a file only when the content differs; returns 0 when the file changed.
write_if_changed() {
  local target="$1" mode="${2:-0644}" tmp
  tmp="$(mktemp)"
  cat > "${tmp}"
  if [[ -f "${target}" ]] && cmp -s "${tmp}" "${target}"; then
    rm -f "${tmp}"
    return 1
  fi
  install -D -m "${mode}" "${tmp}" "${target}"
  rm -f "${tmp}"
  return 0
}

# ---- Time zone ----
log "Time zone Africa/Tunis"
timedatectl set-timezone Africa/Tunis

# ---- System updates and unattended security upgrades ----
log "System updates"
apt-get update -q
apt-get -y -q -o Dpkg::Options::=--force-confold upgrade
# iptables-persistent: do not save the current rules during installation (Docker's included).
echo 'iptables-persistent iptables-persistent/autosave_v4 boolean false' | debconf-set-selections
echo 'iptables-persistent iptables-persistent/autosave_v6 boolean false' | debconf-set-selections
apt-get -y -q install ca-certificates curl gnupg unattended-upgrades fail2ban python3-systemd \
  iptables-persistent
write_if_changed /etc/apt/apt.conf.d/20auto-upgrades <<'EOF' || true
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# ---- Docker Engine and Compose (https://docs.docker.com/engine/install/ubuntu/) ----
log "Docker Engine"
for pkg in docker.io docker-compose docker-compose-v2 docker-doc podman-docker containerd runc; do
  if dpkg -s "${pkg}" > /dev/null 2>&1; then apt-get -y -q remove "${pkg}"; fi
done
install -m 0755 -d /etc/apt/keyrings
if [[ ! -s /etc/apt/keyrings/docker.asc ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi
if write_if_changed /etc/apt/sources.list.d/docker.sources <<EOF; then
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-${VERSION_CODENAME}}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
  apt-get update -q
fi
apt-get -y -q install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
if write_if_changed /etc/docker/daemon.json <<'EOF'; then
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "5" }
}
EOF
  log "Docker log rotation changed: restarting Docker"
  systemctl restart docker
fi
systemctl enable --now docker > /dev/null

# ---- Host firewall: 22, 80 and 443 ----
# Oracle's Ubuntu images end the INPUT chain of /etc/iptables/rules.v4 with a REJECT rule: the
# ACCEPT rules go before it, in the file (kept at boot) and live. The live rules are changed rule
# by rule, never with iptables-restore, which would also wipe Docker's chains.
log "Host firewall"
RULES_V4=/etc/iptables/rules.v4
for port in 80 443; do
  rule="-A INPUT -p tcp -m state --state NEW -m tcp --dport ${port} -j ACCEPT"
  if [[ -f "${RULES_V4}" ]] && ! grep -qxF -- "${rule}" "${RULES_V4}"; then
    if grep -q '^-A INPUT -j REJECT' "${RULES_V4}"; then
      sed -i "0,/^-A INPUT -j REJECT/s//${rule//\//\\/}\n&/" "${RULES_V4}"
    fi
  fi
  live=(-p tcp -m state --state NEW -m tcp --dport "${port}" -j ACCEPT)
  if ! iptables -C INPUT "${live[@]}" 2> /dev/null; then
    reject_line="$(iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
    if [[ -n "${reject_line}" ]]; then
      iptables -I INPUT "${reject_line}" "${live[@]}"
    fi
  fi
done

# ---- SSH: keys only, no root login ----
log "SSH hardening"
admin_user="${SUDO_USER:-}"
if [[ -n "${admin_user}" && "${admin_user}" != root ]]; then
  admin_home="$(getent passwd "${admin_user}" | cut -d: -f6)"
  [[ -s "${admin_home}/.ssh/authorized_keys" ]] \
    || die "${admin_user} has no ~/.ssh/authorized_keys: refusing to disable password logins"
fi
# 10- sorts before Ubuntu's and cloud-init's drop-ins: sshd keeps the first value it reads.
if write_if_changed /etc/ssh/sshd_config.d/10-soukly.conf <<'EOF'; then
# Managed by infra/vm/setup.sh (DEP-02)
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
EOF
  sshd -t
  systemctl reload ssh
fi
if write_if_changed /etc/fail2ban/jail.d/soukly-sshd.local <<'EOF'; then
# Managed by infra/vm/setup.sh (DEP-02)
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
  systemctl restart fail2ban
fi
systemctl enable --now fail2ban > /dev/null

# ---- deploy user (GitHub Actions, DEP-03 and DEP-04) ----
log "User ${DEPLOY_USER}"
if ! id "${DEPLOY_USER}" > /dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
fi
passwd -l "${DEPLOY_USER}" > /dev/null
usermod -aG docker "${DEPLOY_USER}"
deploy_home="$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)"
install -d -m 0700 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${deploy_home}/.ssh"
touch "${deploy_home}/.ssh/authorized_keys"
if [[ -n "${DEPLOY_KEY_FILE}" ]]; then
  key="$(grep -E '^(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa) ' "${DEPLOY_KEY_FILE}" | head -n 1)"
  grep -qxF -- "${key}" "${deploy_home}/.ssh/authorized_keys" \
    || printf '%s\n' "${key}" >> "${deploy_home}/.ssh/authorized_keys"
elif [[ ! -s "${deploy_home}/.ssh/authorized_keys" ]]; then
  log "warning: ${DEPLOY_USER} has no SSH key yet; run again with --deploy-key <file.pub>"
fi
chown "${DEPLOY_USER}:${DEPLOY_USER}" "${deploy_home}/.ssh/authorized_keys"
chmod 0600 "${deploy_home}/.ssh/authorized_keys"

# ---- /opt/soukly ----
log "Directories under ${SOUKLY_ROOT}"
install -d -m 0755 "${SOUKLY_ROOT}"
for dir in src edge dev prod backups; do
  install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${SOUKLY_ROOT}/${dir}"
done
# Every settings file (.env, backup.env) is readable by its owner only.
find "${SOUKLY_ROOT}" -maxdepth 2 -type f -name '*.env' -exec chmod 0600 {} +

# ---- Docker networks shared by the edge Caddy and each stack (fixed subnets, see GATEWAY_TRUSTED_PROXIES) ----
log "Docker networks"
for spec in "soukly-edge-dev 172.30.10.0/24" "soukly-edge-prod 172.30.20.0/24"; do
  read -r name subnet <<< "${spec}"
  if ! docker network inspect "${name}" > /dev/null 2>&1; then
    docker network create --driver bridge --subnet "${subnet}" "${name}" > /dev/null
  fi
done

# ---- Nightly prod backup (infra/backup) ----
log "Prod backup timer"
install -m 0755 "${REPO_INFRA}/backup/pg-backup.sh" /usr/local/sbin/soukly-pg-backup
units_changed=false
for unit in soukly-backup.service soukly-backup.timer; do
  if write_if_changed "/etc/systemd/system/${unit}" < "${REPO_INFRA}/backup/${unit}"; then
    units_changed=true
  fi
done
if [[ "${units_changed}" == true ]]; then systemctl daemon-reload; fi
if [[ -f "${SOUKLY_ROOT}/backups/backup.env" ]]; then
  systemctl enable --now soukly-backup.timer > /dev/null
else
  log "note: ${SOUKLY_ROOT}/backups/backup.env is missing: the backup timer stays disabled (infra/backup/README.md)"
fi

log "Done"
