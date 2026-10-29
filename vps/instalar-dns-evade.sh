#!/usr/bin/env bash
# =============================================================================
#  instalar-dns-evade.sh
#  Monta en un VPS Ubuntu 24.04 la cadena:
#     App ─(DoH)─> Caddy/nginx ─> Blocky :4053 ─> evade-proxy :5335 ─> Unbound :5336
#  Resultado: DNS por HTTPS en  https://<TU_DOMINIO>/dns-query
#
#  Uso (como root):
#     bash instalar-dns-evade.sh tudominio.com
#
#  Se puede volver a ejecutar sin romper nada (actualiza lo que ya exista).
#  No abre ningún puerto nuevo: todo escucha en 127.0.0.1 y solo se expone
#  a través de tu servidor web en el 443.
# =============================================================================
set -euo pipefail

DOMINIO="${1:-}"
REPO_URL="https://github.com/Oihalitz/xdp-dns-evadeproxy"
SRC_DIR="/root/xdp-dns-evadeproxy"
UNBOUND_PORT=5336
EVADE_PORT=5335
BLOCKY_HTTP_PORT=4053
BLOCKY_DNS_PORT=5354

verde()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
aviso()  { printf '\033[1;33m[!] %s\033[0m\n' "$*"; }
error()  { printf '\033[1;31m[x] %s\033[0m\n' "$*" >&2; exit 1; }

# --- Comprobaciones previas ---------------------------------------------------
[[ "$(id -u)" -eq 0 ]] || error "Ejecútalo como root (sudo -i)."
[[ -n "$DOMINIO" ]]    || error "Falta el dominio. Uso: bash $0 tudominio.com"

for p in $UNBOUND_PORT $EVADE_PORT $BLOCKY_HTTP_PORT $BLOCKY_DNS_PORT; do
    if ss -lntup 2>/dev/null | grep -q ":$p "; then
        dueño=$(ss -lntup | grep ":$p " | grep -o 'users:(("[^"]*' | head -1 | cut -d'"' -f2)
        case "$dueño" in
            unbound|evade-proxy|blocky) ;;   # ya es nuestro (re-ejecución)
            *) error "El puerto $p ya lo usa '$dueño'. Libéralo o cambia el puerto arriba." ;;
        esac
    fi
done

case "$(uname -m)" in
    x86_64)  ARCH="x86_64" ;;
    aarch64) ARCH="arm64"  ;;
    *) error "Arquitectura $(uname -m) no soportada por este script." ;;
esac

# --- 1. Paquetes --------------------------------------------------------------
verde "1/7 Instalando paquetes del sistema"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q git curl python3 dnsutils build-essential pkg-config unbound ca-certificates

# --- 2. Swap temporal si hay poca RAM (compilar Rust la necesita) -------------
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
SWAP_TEMP=""
if (( MEM_MB < 1800 )) && ! swapon --show | grep -q .; then
    verde "2/7 Poca RAM (${MEM_MB} MB): creando swap temporal de 2 GB para compilar"
    fallocate -l 2G /swap-compilar
    chmod 600 /swap-compilar
    mkswap /swap-compilar >/dev/null
    swapon /swap-compilar
    SWAP_TEMP="/swap-compilar"
else
    verde "2/7 RAM suficiente (${MEM_MB} MB)"
fi

# --- 3. Rust ------------------------------------------------------------------
verde "3/7 Rust"
if ! command -v cargo >/dev/null; then
    [[ -x "$HOME/.cargo/bin/cargo" ]] || curl -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
fi
# shellcheck disable=SC1091
source "$HOME/.cargo/env"
rustup update stable >/dev/null 2>&1 || true
cargo --version

# --- 4. Unbound en 127.0.0.1:5336 ---------------------------------------------
verde "4/7 Configurando Unbound en 127.0.0.1:${UNBOUND_PORT}"
cat > /etc/unbound/unbound.conf.d/evade.conf <<EOF
# Generado por instalar-dns-evade.sh
server:
    interface: 127.0.0.1
    port: ${UNBOUND_PORT}
    access-control: 127.0.0.0/8 allow
    do-ip4: yes
    do-ip6: yes
    prefetch: yes
    num-threads: 2
    hide-identity: yes
    hide-version: yes
EOF
unbound-checkconf >/dev/null || error "La configuración de Unbound no es válida."
systemctl enable unbound >/dev/null 2>&1
systemctl restart unbound
sleep 2
dig @127.0.0.1 -p $UNBOUND_PORT google.com +short +time=5 | grep -q . \
    || error "Unbound no responde en el puerto $UNBOUND_PORT (mira: journalctl -u unbound)."
echo "Unbound OK"

# --- 5. evade-proxy -----------------------------------------------------------
verde "5/7 Descargando y compilando evade-proxy (puede tardar varios minutos)"
if [[ -d "$SRC_DIR/.git" ]]; then
    git -C "$SRC_DIR" pull --ff-only
else
    git clone "$REPO_URL" "$SRC_DIR"
fi
# El script oficial compila, instala servicios y descarga la primera lista
( cd "$SRC_DIR" && ./scripts/install.sh )
# Si ya existía el binario, forzamos reinicio para cargar la versión nueva
systemctl restart xdp-evade-proxy
sleep 2
dig @127.0.0.1 -p $EVADE_PORT google.com +short +time=5 | grep -q . \
    || error "evade-proxy no responde (mira: journalctl -u xdp-evade-proxy)."
echo "evade-proxy OK"

# La sonda residencial no se usa: nos aseguramos de que su servicio no arranque
systemctl disable --now xdp-probe-server >/dev/null 2>&1 || true

# --- 6. Blocky (servidor DoH) -------------------------------------------------
verde "6/7 Instalando Blocky (DoH en 127.0.0.1:${BLOCKY_HTTP_PORT})"
BLOCKY_URL=$(curl -fsSL https://api.github.com/repos/0xERR0R/blocky/releases/latest \
    | python3 -c "import sys,json; a=[x['browser_download_url'] for x in json.load(sys.stdin)['assets'] if x['name'].endswith('Linux_${ARCH}.tar.gz')]; print(a[0] if a else '')")
[[ -n "$BLOCKY_URL" ]] || error "No encuentro la descarga de Blocky para ${ARCH}."
TMPD=$(mktemp -d)
curl -fsSL "$BLOCKY_URL" -o "$TMPD/blocky.tgz"
tar -xzf "$TMPD/blocky.tgz" -C "$TMPD"
install -m 0755 "$TMPD/blocky" /usr/local/bin/blocky
rm -rf "$TMPD"

id blocky >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin blocky
mkdir -p /etc/blocky
cat > /etc/blocky/config.yml <<EOF
# Generado por instalar-dns-evade.sh
upstreams:
  groups:
    default:
      - 127.0.0.1:${EVADE_PORT}
  timeout: 5s
# Sin caché propia: evade-proxy reescribe al vuelo y Unbound ya cachea
caching:
  maxTime: -1
ports:
  dns: 127.0.0.1:${BLOCKY_DNS_PORT}
  http: 127.0.0.1:${BLOCKY_HTTP_PORT}
log:
  level: warn
  privacy: true
EOF
chown -R root:blocky /etc/blocky
chmod 0640 /etc/blocky/config.yml

cat > /etc/systemd/system/blocky.service <<'EOF'
[Unit]
Description=Blocky (DoH delante de evade-proxy)
After=network.target xdp-evade-proxy.service
Wants=xdp-evade-proxy.service

[Service]
User=blocky
Group=blocky
ExecStart=/usr/local/bin/blocky --config /etc/blocky/config.yml
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable blocky >/dev/null 2>&1
systemctl restart blocky
sleep 3

# Prueba: consulta DoH real (example.com, tipo A) contra Blocky
CODIGO=$(curl -s -o /dev/null -w '%{http_code}' \
    "http://127.0.0.1:${BLOCKY_HTTP_PORT}/dns-query?dns=AAABAAABAAAAAAAAB2V4YW1wbGUDY29tAAABAAE")
[[ "$CODIGO" == "200" ]] || error "Blocky no responde a DoH (código $CODIGO). Mira: journalctl -u blocky"
echo "Blocky DoH OK"

# --- Limpieza de swap temporal ------------------------------------------------
if [[ -n "$SWAP_TEMP" ]]; then
    swapoff "$SWAP_TEMP" && rm -f "$SWAP_TEMP"
fi

# --- 7. Servidor web ----------------------------------------------------------
verde "7/7 Publicar /dns-query en https://${DOMINIO}"
if command -v caddy >/dev/null; then
    cat <<EOF
Tienes Caddy. Edita /etc/caddy/Caddyfile y, DENTRO del bloque de ${DOMINIO} { ... },
añade estas líneas (arriba del todo del bloque):

    handle /dns-query* {
        reverse_proxy 127.0.0.1:${BLOCKY_HTTP_PORT}
    }

Después:
    caddy validate --config /etc/caddy/Caddyfile && systemctl reload caddy
EOF
elif command -v nginx >/dev/null; then
    cat <<EOF
Tienes nginx. En el bloque server { ... } de ${DOMINIO} con 'listen 443 ssl', añade:

    location /dns-query {
        proxy_pass http://127.0.0.1:${BLOCKY_HTTP_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$remote_addr;
    }

Después:
    nginx -t && systemctl reload nginx
EOF
else
    aviso "No encuentro Caddy ni nginx. Hace falta uno con HTTPS para exponer /dns-query."
fi

cat <<EOF

=====================================================================
 Instalado.
   Unbound      : systemctl status unbound
   evade-proxy  : systemctl status xdp-evade-proxy
   Lista de IPs : systemctl status update-blocked-ips.timer   (cada 15 s)
   Blocky (DoH) : systemctl status blocky
   Métricas     : curl -s http://127.0.0.1:5339/metrics

 Cuando hayas añadido el bloque al servidor web, prueba desde fuera:
   curl -s -o /dev/null -w '%{http_code}\n' \\
     "https://${DOMINIO}/dns-query?dns=AAABAAABAAAAAAAAB2V4YW1wbGUDY29tAAABAAE"
   (tiene que devolver 200)

 URL DoH para las apps:  https://${DOMINIO}/dns-query
=====================================================================
EOF
