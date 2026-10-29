#!/usr/bin/env bash
# =====================================================================
#  Proxy inverso Xtream Codes (nginx) para Ubuntu 24.04
#  - Pasa la IP real del cliente al servidor principal
#  - Soporta servidor principal detrás de Cloudflare
#  - Sigue internamente los redirects a load balancers
#  - Reescribe las URLs de get.php / player_api.php / m3u8 al proxy
# =====================================================================
set -euo pipefail

# ======================= CONFIGURA AQUÍ ==============================
UPSTREAM_HOST="cf.redonleet.xyz" # Dominio del servidor principal Xtream (el de Cloudflare)
UPSTREAM_SCHEME="http"                  # A Cloudflare se entra por HTTP
UPSTREAM_PORT="80"                      # 80, 8080, 8880, 2052, 2082, 2086, 2095
ORIGIN_IP=""                            # OPCIONAL: IP real del servidor Xtream para saltarse Cloudflare
PROXY_DOMAIN="proxy.redonbcn.xyz"       # Dominio de este VPS
LISTEN_PORT="80"                        # Puerto en el que escuchará este proxy
# =====================================================================

if [[ $EUID -ne 0 ]]; then echo "Ejecuta como root (sudo)."; exit 1; fi

UPSTREAM_ADDR="${ORIGIN_IP:-$UPSTREAM_HOST}"

echo ">> Instalando nginx..."
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y nginx curl

echo ">> Ajustes de red del kernel (BBR, colas, puertos)..."
cat > /etc/sysctl.d/99-xc-proxy.conf <<'EOF'
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.ip_local_port_range = 10240 65535
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_tw_reuse = 1
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
fs.file-max = 2097152
EOF
sysctl --system >/dev/null

echo ">> Límites de ficheros para nginx..."
mkdir -p /etc/systemd/system/nginx.service.d
cat > /etc/systemd/system/nginx.service.d/limits.conf <<'EOF'
[Service]
LimitNOFILE=1048576
EOF
systemctl daemon-reload

NGX=/etc/nginx/nginx.conf
grep -q worker_rlimit_nofile "$NGX" || sed -i '/^worker_processes/a worker_rlimit_nofile 1048576;' "$NGX"
sed -i 's/worker_connections [0-9]*;/worker_connections 16384;/' "$NGX"
sed -i 's/#\s*multi_accept on;/multi_accept on;/' "$NGX"

echo ">> Snippet de IP real del cliente..."
mkdir -p /etc/nginx/snippets
cat > /etc/nginx/snippets/xc-realip.conf <<'EOF'
# IP real del cliente hacia el servidor principal.
# Se fija (no se concatena) para que el cliente no pueda falsearla.
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $http_host;
proxy_set_header Connection        "";
proxy_set_header Accept-Encoding   "";             # necesario para poder reescribir URLs
EOF
# CF-Connecting-IP solo se puede enviar en modo directo al origen.
# Si va por Cloudflare, incluir esa cabecera provoca "error 1000" y corta la conexión.
if [[ -n "$ORIGIN_IP" ]]; then
  cat >> /etc/nginx/snippets/xc-realip.conf <<'EOF'
proxy_set_header CF-Connecting-IP  $remote_addr;
proxy_set_header True-Client-IP    $remote_addr;
EOF
else
  cat >> /etc/nginx/snippets/xc-realip.conf <<'EOF'
# Vía Cloudflare: NO se envía CF-Connecting-IP (Cloudflare la rechaza con error 1000).
# La IP del cliente viaja en X-Forwarded-For; el origen debe leerla desde ahí.
EOF
fi

echo ">> IP real si ${PROXY_DOMAIN} pasa también por Cloudflare (nube naranja)..."
CF4=$(curl -fsS --max-time 10 https://www.cloudflare.com/ips-v4 || true)
CF6=$(curl -fsS --max-time 10 https://www.cloudflare.com/ips-v6 || true)
[[ -z "$CF4" ]] && CF4="173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 104.24.0.0/14 172.64.0.0/13 131.0.72.0/22"
[[ -z "$CF6" ]] && CF6="2400:cb00::/32 2606:4700::/32 2803:f800::/32 2405:b500::/32 2405:8100::/32 2a06:98c0::/29 2c0f:f248::/32"
{
  echo "# Si el cliente llega a través de Cloudflare, recuperar su IP real."
  echo "# Si el dominio del proxy está en nube gris, esto no afecta."
  for r in $CF4 $CF6; do echo "set_real_ip_from $r;"; done
  echo "real_ip_header CF-Connecting-IP;"
} > /etc/nginx/snippets/xc-cf-incoming.conf

echo ">> Reglas de reescritura de URLs..."
SUBS=/etc/nginx/snippets/xc-subfilter.conf
{
  echo "# URLs del servidor principal -> URL del proxy (primero las variantes con puerto)"
  for s in https http; do
    printf "sub_filter '%s://%s:%s' '\$scheme://\$http_host';\n"       "$s" "$UPSTREAM_HOST" "$UPSTREAM_PORT"
    printf "sub_filter '%s:\\\\/\\\\/%s:%s' '\$scheme:\\\\/\\\\/\$http_host';\n" "$s" "$UPSTREAM_HOST" "$UPSTREAM_PORT"
  done
  for s in https http; do
    printf "sub_filter '%s://%s' '\$scheme://\$http_host';\n"       "$s" "$UPSTREAM_HOST"
    printf "sub_filter '%s:\\\\/\\\\/%s' '\$scheme:\\\\/\\\\/\$http_host';\n" "$s" "$UPSTREAM_HOST"
  done
  printf "sub_filter '\"url\":\"%s\"' '\"url\":\"\$host\"';\n" "$UPSTREAM_HOST"
  echo "sub_filter_once off;"
  echo "sub_filter_types application/json text/plain text/xml application/xml audio/x-mpegurl audio/mpegurl application/x-mpegurl application/vnd.apple.mpegurl;"
} > "$SUBS"

echo ">> Sitio nginx del proxy..."
cat > /etc/nginx/sites-available/xc-proxy <<'EOF'
resolver 1.1.1.1 8.8.8.8 valid=300s ipv6=off;
resolver_timeout 5s;

upstream xc_backend {
    server __UPSTREAM_ADDR__:__UPSTREAM_PORT__;
    keepalive 64;
}

server {
    listen __LISTEN_PORT__ default_server;
    listen [::]:__LISTEN_PORT__ default_server;
    server_name __PROXY_DOMAIN__ _;

    include /etc/nginx/snippets/xc-cf-incoming.conf;

    access_log /var/log/nginx/xc-proxy.access.log;
    error_log  /var/log/nginx/xc-proxy.error.log warn;

    proxy_http_version 1.1;

    # TLS hacia Cloudflare / origen (SNI obligatorio con Cloudflare)
    proxy_ssl_server_name on;
    proxy_ssl_name __UPSTREAM_HOST__;
    proxy_ssl_protocols TLSv1.2 TLSv1.3;

    # Streaming en directo: sin buffers y timeouts largos
    proxy_buffering off;
    proxy_request_buffering off;
    proxy_max_temp_file_size 0;
    proxy_connect_timeout 15s;
    proxy_send_timeout 3600s;
    proxy_read_timeout 3600s;
    tcp_nodelay on;

    include /etc/nginx/snippets/xc-subfilter.conf;

    location = /xc-proxy-health {
        access_log off;
        return 200 "ok\n";
    }

    location / {
        include /etc/nginx/snippets/xc-realip.conf;
        proxy_set_header Host __UPSTREAM_HOST__;
        proxy_pass __UPSTREAM_SCHEME__://xc_backend;

        # Los redirects a load balancers se siguen aquí, el cliente nunca los ve
        proxy_intercept_errors on;
        recursive_error_pages on;
        error_page 301 302 303 307 308 = @xc_redirect;
    }

    location @xc_redirect {
        set $xc_target $upstream_http_location;
        include /etc/nginx/snippets/xc-realip.conf;   # Host = host del redirect
        proxy_ssl_name $proxy_host;
        proxy_pass $xc_target;

        proxy_intercept_errors on;
        recursive_error_pages on;
        error_page 301 302 303 307 308 = @xc_redirect;
    }
}
EOF

sed -i \
  -e "s|__UPSTREAM_ADDR__|${UPSTREAM_ADDR}|g" \
  -e "s|__UPSTREAM_PORT__|${UPSTREAM_PORT}|g" \
  -e "s|__UPSTREAM_HOST__|${UPSTREAM_HOST}|g" \
  -e "s|__UPSTREAM_SCHEME__|${UPSTREAM_SCHEME}|g" \
  -e "s|__LISTEN_PORT__|${LISTEN_PORT}|g" \
  -e "s|__PROXY_DOMAIN__|${PROXY_DOMAIN}|g" \
  /etc/nginx/sites-available/xc-proxy

rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/xc-proxy /etc/nginx/sites-enabled/xc-proxy

nginx -t
systemctl enable nginx >/dev/null
systemctl restart nginx

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "${LISTEN_PORT}/tcp"
fi

IP=$(curl -s4 https://api.ipify.org || echo "IP_DEL_VPS")
echo
echo "=================================================================="
echo " Proxy listo en: http://${PROXY_DOMAIN}:${LISTEN_PORT}  (IP del VPS: ${IP})"
echo " Modo: $([[ -n "$ORIGIN_IP" ]] && echo "directo al origen (${ORIGIN_IP})" || echo "a través de Cloudflare")"
echo
echo " Pruebas:"
echo "  curl http://${IP}:${LISTEN_PORT}/xc-proxy-health"
echo "  curl 'http://${IP}:${LISTEN_PORT}/player_api.php?username=USER&password=PASS'"
echo "  curl -s 'http://${IP}:${LISTEN_PORT}/get.php?username=USER&password=PASS&type=m3u_plus' | head"
echo " Logs: tail -f /var/log/nginx/xc-proxy.error.log"
echo "=================================================================="
