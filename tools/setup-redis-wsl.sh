#!/usr/bin/env bash
# Run as root in the project's Ubuntu-24.04 WSL distribution.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo 'Run this script as root.' >&2
  exit 1
fi
. /etc/os-release
if [ "$ID" != ubuntu ] || [ "$VERSION_ID" != 24.04 ]; then
  echo 'This installer is scoped to Ubuntu 24.04.' >&2
  exit 1
fi
if [ "$(ps -p 1 -o comm=)" != systemd ]; then
  echo 'Enable systemd in WSL before running this installer.' >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=30 update
candidate=$(apt-cache policy redis-server | awk '/Candidate:/ {print $2}')
case "$candidate" in
  7.*|*:7.*) ;;
  *) echo "Redis 7 required; package candidate is $candidate" >&2; exit 1 ;;
esac
apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=30 install -y --no-install-recommends redis-server redis-tools
redis-server --version
redis-server --version | grep -Eq 'v=7\.'

# Keep security/patch updates available within the chosen major version.
cat > /etc/apt/preferences.d/crm-redis7 <<'PIN'
Package: redis-server redis-tools
Pin: version 5:7.*
Pin-Priority: 1001
PIN

scratch_dir=$(mktemp -d)
trap 'rm -rf -- "$scratch_dir"' EXIT
cat > "$scratch_dir/crm-redis.conf" <<'CONFIG'
bind 127.0.0.1 -::1
protected-mode yes
port 6380
daemonize no
supervised systemd
loglevel notice
logfile ""
dir /var/lib/redis-crm
dbfilename dump.rdb
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfilename "appendonly.aof"
appenddirname "appendonlydir"
appendfsync everysec
CONFIG
cat > "$scratch_dir/crm-redis.service" <<'UNIT'
[Unit]
Description=CRM development Redis 7 on port 6380
After=network.target

[Service]
Type=notify
User=redis
Group=redis
RuntimeDirectory=redis-crm
RuntimeDirectoryMode=0750
WorkingDirectory=/var/lib/redis-crm
ExecStart=/usr/bin/redis-server /etc/redis/crm-redis.conf
Restart=on-failure
RestartSec=3
TimeoutStartSec=60
TimeoutStopSec=90

[Install]
WantedBy=multi-user.target
UNIT
cat > "$scratch_dir/crm-redis-keepalive" <<'KEEPALIVE'
#!/usr/bin/env bash
set -euo pipefail
exec 9>/run/crm-redis-keepalive.lock
flock -n 9 || exit 0
systemctl start crm-redis.service
exec -a crm-redis-keepalive /usr/bin/sleep infinity
KEEPALIVE

for target in /etc/redis/crm-redis.conf /etc/systemd/system/crm-redis.service /usr/local/sbin/crm-redis-keepalive; do
  source_file="$scratch_dir/$(basename "$target")"
  if [ -e "$target" ] && ! cmp -s "$source_file" "$target"; then
    echo "Existing file differs; preserved for review: $target" >&2
    exit 1
  fi
done

# Only the newly installed Linux package service is disabled. Windows Redis is separate.
systemctl disable --now redis-server.service
install -d -o redis -g redis -m 0750 /var/lib/redis-crm
install -o root -g redis -m 0640 "$scratch_dir/crm-redis.conf" /etc/redis/crm-redis.conf
install -o root -g root -m 0644 "$scratch_dir/crm-redis.service" /etc/systemd/system/crm-redis.service
install -o root -g root -m 0755 "$scratch_dir/crm-redis-keepalive" /usr/local/sbin/crm-redis-keepalive
systemctl daemon-reload
systemctl enable --now crm-redis.service
systemctl is-active crm-redis.service
redis-cli -h 127.0.0.1 -p 6380 PING
redis-cli -h 127.0.0.1 -p 6380 INFO server | grep '^redis_version:'
