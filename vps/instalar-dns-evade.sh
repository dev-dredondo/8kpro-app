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
verde "1/6 Instalando paquetes del sistema"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q git curl python3 dnsutils unbound unbound-anchor dns-root-data ca-certificates

# --- 2. Disco -------------------------------------------------------------------
# Ya no se compila nada en el VPS: basta con ~300 MB libres.
LIBRE_MB=$(df -Pm / | awk 'NR==2 {print $4}')
if (( LIBRE_MB < 300 )); then
    error "Solo quedan ${LIBRE_MB} MB libres en disco; hacen falta ~300 MB. Prueba: apt clean; journalctl --vacuum-size=100M"
fi
verde "2/6 Disco OK (${LIBRE_MB} MB libres)"
if [[ -d "$HOME/.rustup" ]] && [[ "${CONSERVAR_RUST:-0}" != "1" ]]; then
    aviso "Hay un Rust instalado en $HOME/.rustup (lo dejó una versión anterior de este script)."
    aviso "Ya no hace falta. Para liberar ~600 MB:  rustup self uninstall -y"
fi

# --- 4. Unbound en 127.0.0.1:5336 ---------------------------------------------
verde "3/6 Configurando Unbound en 127.0.0.1:${UNBOUND_PORT}"
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
# Ubuntu crea la clave DNSSEC raíz al arrancar el servicio; la generamos ya
# para que unbound-checkconf no falle en una instalación recién hecha.
mkdir -p /var/lib/unbound
if [[ ! -s /var/lib/unbound/root.key ]]; then
    unbound-anchor -a /var/lib/unbound/root.key || true
    [[ -s /var/lib/unbound/root.key ]] || cp /usr/share/dns/root.key /var/lib/unbound/root.key 2>/dev/null || true
fi
chown -R unbound:unbound /var/lib/unbound
unbound-checkconf >/dev/null || error "La configuración de Unbound no es válida."
systemctl enable unbound >/dev/null 2>&1
systemctl restart unbound
sleep 2
dig @127.0.0.1 -p $UNBOUND_PORT google.com +short +time=5 | grep -q . \
    || error "Unbound no responde en el puerto $UNBOUND_PORT (mira: journalctl -u unbound)."
echo "Unbound OK"

# --- 4. evade-proxy (binario precompilado, sin Rust) ------------------------
verde "4/6 Instalando evade-proxy"
[[ "$ARCH" == "x86_64" ]] || error "El binario precompilado solo existe para x86_64."
BIN_BASE="https://github.com/dev-dredondo/8kpro-app/releases/download/evade-proxy-bin"
TMPB=$(mktemp -d)
curl -fsSL "$BIN_BASE/evade-proxy-linux-x86_64"        -o "$TMPB/evade-proxy-linux-x86_64" \
    || error "No se pudo descargar el binario de evade-proxy desde $BIN_BASE"
curl -fsSL "$BIN_BASE/evade-proxy-linux-x86_64.sha256" -o "$TMPB/sum.sha256"
(cd "$TMPB" && sha256sum -c sum.sha256 >/dev/null) || error "La suma SHA-256 del binario no coincide."
COMMIT=$(curl -fsSL "$BIN_BASE/evade-proxy-commit.txt" 2>/dev/null | tr -d '[:space:]' || true)

# Scripts y unidades systemd del proyecto, en el mismo commit que el binario
PREFIX=/opt/xdp-dns-evadeproxy
rm -rf "$SRC_DIR"
git clone -q --filter=blob:none "$REPO_URL" "$SRC_DIR"
[[ -n "$COMMIT" ]] && git -C "$SRC_DIR" -c advice.detachedHead=false checkout -q "$COMMIT"

install -m 0755 "$TMPB/evade-proxy-linux-x86_64" /usr/local/bin/evade-proxy
rm -rf "$TMPB"
mkdir -p "$PREFIX" /etc/unbound /var/lib/evade-proxy "$PREFIX/data"
tar -C "$SRC_DIR" --exclude .git -cf - . | tar -C "$PREFIX" -xf -
rm -rf "$SRC_DIR"
[[ -f /etc/evade-proxy.env ]] || install -m 0600 "$PREFIX/evade-proxy.env.example" /etc/evade-proxy.env
for u in xdp-evade-proxy.service update-blocked-ips.service update-blocked-ips.timer; do
    install -m 0644 "$PREFIX/systemd/$u" "/etc/systemd/system/$u"
done
systemctl daemon-reload
echo "Descargando lista de IPs bloqueadas y rangos de Cloudflare…"
XDP_PREFIX="$PREFIX" "$PREFIX/scripts/update-cloudflare-prefixes.py" || aviso "No se pudieron bajar los rangos de Cloudflare (se reintentará solo)."
XDP_PREFIX="$PREFIX" "$PREFIX/scripts/update-blocked-ips.sh" || aviso "No se pudo bajar la lista de IPs (se reintentará cada 15 s)."
systemctl enable --now update-blocked-ips.timer >/dev/null 2>&1
systemctl enable xdp-evade-proxy >/dev/null 2>&1
systemctl restart xdp-evade-proxy
sleep 2
dig @127.0.0.1 -p $EVADE_PORT google.com +short +time=5 | grep -q . \
    || error "evade-proxy no responde (mira: journalctl -u xdp-evade-proxy)."
echo "evade-proxy OK ($(/usr/local/bin/evade-proxy --version 2>/dev/null || echo "commit ${COMMIT:0:7}"))"

# La sonda residencial no se usa: nos aseguramos de que su servicio no arranque
systemctl disable --now xdp-probe-server >/dev/null 2>&1 || true

# --- 6. Blocky (servidor DoH) -------------------------------------------------
verde "5/6 Instalando Blocky (DoH en 127.0.0.1:${BLOCKY_HTTP_PORT})"
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

# --- 6. Servidor web ----------------------------------------------------------
verde "6/6 Publicar /dns-query en https://${DOMINIO}"
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
