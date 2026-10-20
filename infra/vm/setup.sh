#!/usr/bin/env bash
# Prepares the VM (Ubuntu 24.04, amd64 or arm64, any provider) for Soukly (DEP-02). Idempotent:
# running it again changes nothing that is already in place and duplicates nothing.
#
#   sudo bash infra/vm/setup.sh --deploy-key /path/to/deploy.pub   # first run, from an admin account
#   sudo bash infra/vm/setup.sh                                    # later runs keep the existing key
#   sudo bash infra/vm/setup.sh --dev-key /path/to/dev.pub          # restricted key of deploy-dev (DEP-03)
#   sudo bash infra/vm/setup.sh --prod-key /path/to/prod.pub        # restricted key of deploy-prod (DEP-04)
#   bash infra/vm/setup.sh --admin-user sofien --deploy-key ...    # first run as root (e.g. Hetzner)
#
# Root logins are turned off, so when the provider only gives a root account, --admin-user creates
# an admin account (sudo without password, root's SSH keys) to log in with from then on.
#
# What it does: system updates and unattended security upgrades, Docker Engine and Compose from
# Docker's apt repository, Docker log rotation, host firewall (22, 80, 443), SSH keys only,
# fail2ban, the "deploy" user, /opt/soukly, the deployment script and the restricted SSH keys of
# the deploy-dev and deploy-prod workflows, the Docker networks shared with the edge Caddy, the
# nightly prod backup timer, and the Africa/Tunis time zone.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_INFRA="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEPLOY_USER=deploy
SOUKLY_ROOT=/opt/soukly
DEPLOY_KEY_FILE=""
DEV_KEY_FILE=""
PROD_KEY_FILE=""
ADMIN_USER=""

log() { printf '\n[setup] %s\n' "$*"; }
die() { printf '[setup] ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-key) DEPLOY_KEY_FILE="${2:-}"; shift 2 ;;
    --admin-user) ADMIN_USER="${2:-}"; shift 2 ;;
    --dev-key) DEV_KEY_FILE="${2:-}"; shift 2 ;;
    --prod-key) PROD_KEY_FILE="${2:-}"; shift 2 ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ $EUID -eq 0 ]] || die "run with sudo"
# Root logins are turned off below: someone must still be able to log in afterwards.
if [[ -z "${ADMIN_USER}" && ( -z "${SUDO_USER:-}" || "${SUDO_USER:-}" == root ) ]]; then
  die "run from an admin account with sudo, or as root with --admin-user <name>"
fi
# shellcheck source=/dev/null
. /etc/os-release
[[ "${ID}" == ubuntu ]] || die "Ubuntu expected, found ${ID}"
[[ "${VERSION_ID}" == 24.04 ]] || log "warning: written for Ubuntu 24.04, found ${VERSION_ID}"
if [[ -n "${ADMIN_USER}" ]]; then
  [[ "${ADMIN_USER}" =~ ^[a-z][a-z0-9_-]{0,31}$ && "${ADMIN_USER}" != deploy ]] \
    || die "invalid admin user name: ${ADMIN_USER}"
fi
for key_file in "${DEPLOY_KEY_FILE}" "${DEV_KEY_FILE}" "${PROD_KEY_FILE}"; do
  [[ -n "${key_file}" ]] || continue
  [[ -r "${key_file}" ]] || die "cannot read ${key_file}"
  grep -Eq '^(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa) ' "${key_file}" \
    || die "${key_file} is not an SSH public key"
done
# One key per environment: the dev key must never deploy prod.
if [[ -n "${DEV_KEY_FILE}" && -n "${PROD_KEY_FILE}" ]] \
  && [[ "$(awk '{ print $2; exit }' "${DEV_KEY_FILE}")" == "$(awk '{ print $2; exit }' "${PROD_KEY_FILE}")" ]]; then
  die "the dev and prod keys must be different"
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
# Some images (Oracle's Ubuntu images) end the INPUT chain of /etc/iptables/rules.v4 with a REJECT
# rule: the ACCEPT rules go before it, in the file (kept at boot) and live. The live rules are
# changed rule by rule, never with iptables-restore, which would also wipe Docker's chains. Images
# without such a rule are left as they are: the provider's firewall limits the ports there.
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

# ---- Admin account, when the provider only gives root ----
if [[ -n "${ADMIN_USER}" ]]; then
  log "Admin user ${ADMIN_USER}"
  [[ -s /root/.ssh/authorized_keys ]] || die "root has no ~/.ssh/authorized_keys to copy"
  if ! id "${ADMIN_USER}" > /dev/null 2>&1; then
    useradd --create-home --shell /bin/bash --groups sudo "${ADMIN_USER}"
  fi
  passwd -l "${ADMIN_USER}" > /dev/null
  usermod -aG sudo "${ADMIN_USER}"
  admin_home="$(getent passwd "${ADMIN_USER}" | cut -d: -f6)"
  install -d -m 0700 -o "${ADMIN_USER}" -g "${ADMIN_USER}" "${admin_home}/.ssh"
  touch "${admin_home}/.ssh/authorized_keys"
  while read -r key; do
    [[ -z "${key}" || "${key}" == \#* ]] && continue
    grep -qxF -- "${key}" "${admin_home}/.ssh/authorized_keys" \
      || printf '%s\n' "${key}" >> "${admin_home}/.ssh/authorized_keys"
  done < /root/.ssh/authorized_keys
  chown "${ADMIN_USER}:${ADMIN_USER}" "${admin_home}/.ssh/authorized_keys"
  chmod 0600 "${admin_home}/.ssh/authorized_keys"
  # The account has no password, so sudo must not ask for one (as on the providers' own images).
  sudoers_tmp="$(mktemp)"
  printf '%s ALL=(ALL) NOPASSWD:ALL\n' "${ADMIN_USER}" > "${sudoers_tmp}"
  visudo -cqf "${sudoers_tmp}" || die "invalid sudoers entry"
  install -m 0440 "${sudoers_tmp}" /etc/sudoers.d/90-soukly-admin
  rm -f "${sudoers_tmp}"
fi

# ---- SSH: keys only, no root login ----
log "SSH hardening"
admin_user="${ADMIN_USER:-${SUDO_USER:-}}"
if [[ -z "${admin_user}" || "${admin_user}" == root ]]; then
  # Logged in as root: turning root logins off would lock everyone out.
  die "run from an admin account with sudo, or as root with --admin-user <name>"
fi
admin_home="$(getent passwd "${admin_user}" | cut -d: -f6)"
[[ -s "${admin_home}/.ssh/authorized_keys" ]] \
  || die "${admin_user} has no ~/.ssh/authorized_keys: refusing to disable password logins"
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

# ---- Deployment script and restricted keys of the deploy workflows (DEP-03) ----
# Owned by root: the deploy user runs the script but cannot change it.
log "Deployment script"
install -d -m 0755 "${SOUKLY_ROOT}/bin"
install -m 0755 "${SCRIPT_DIR}/deploy.sh" "${SOUKLY_ROOT}/bin/deploy.sh"
# A workflow key can only run "deploy.sh <env>" (the SHA arrives in SSH_ORIGINAL_COMMAND): no
# terminal, no forwarding, no other command, no other environment. Any earlier line of the same key
# is replaced.
restrict_key() {
  local env_name="$1" key_file="$2" key blob tmp
  key="$(grep -E '^(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa) ' "${key_file}" | head -n 1)"
  blob="$(awk '{ print $2 }' <<< "${key}")"
  tmp="$(mktemp)"
  grep -vF -- "${blob}" "${deploy_home}/.ssh/authorized_keys" > "${tmp}" || true
  printf 'restrict,command="%s/bin/deploy.sh %s" %s\n' "${SOUKLY_ROOT}" "${env_name}" "${key}" >> "${tmp}"
  if ! cmp -s "${tmp}" "${deploy_home}/.ssh/authorized_keys"; then
    install -m 0600 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${tmp}" "${deploy_home}/.ssh/authorized_keys"
    log "${env_name} deployment key installed (restricted to deploy.sh ${env_name})"
  fi
  rm -f "${tmp}"
}
if [[ -n "${DEV_KEY_FILE}" ]]; then restrict_key dev "${DEV_KEY_FILE}"; fi
if [[ -n "${PROD_KEY_FILE}" ]]; then restrict_key prod "${PROD_KEY_FILE}"; fi

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
