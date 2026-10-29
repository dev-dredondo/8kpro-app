#!/usr/bin/env bash
# =============================================================================
#  dns-puerto53.sh
#  Permite poner la IP del VPS como DNS en el router (DNS clásico, puerto 53).
#  Requiere haber ejecutado antes instalar-dns-evade.sh.
#
#  Seguridad: el puerto 53 SOLO responde a las IPs autorizadas (tu casa).
#  Un DNS abierto a todo internet se usa para ataques y el proveedor del VPS
#  acaba suspendiéndolo.
#
#  - Al instalar se autoriza sola la IP desde la que estás conectado por SSH.
#  - Si la IP de tu casa cambia, abre desde casa (wifi) el enlace secreto
#    que imprime este script y queda autorizada la nueva.
#  - Se recuerdan las 3 últimas IPs autorizadas.
#
#  Uso (como root):   bash dns-puerto53.sh
#  Ver enlace/IPs:    bash dns-puerto53.sh --estado
#  Autorizar una IP:  bash dns-puerto53.sh --autorizar 1.2.3.4
# =============================================================================
set -euo pipefail

CONF_DIR=/etc/dns-evade
LISTA="$CONF_DIR/permitidos.txt"
CLAVE_F="$CONF_DIR/clave"
FW=/usr/local/sbin/dns-evade-fw
AUTH_PY=/usr/local/sbin/dns-evade-autorizar.py
AUTH_PORT=8053
MAX_IPS=3

verde()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
aviso()  { printf '\033[1;33m[!] %s\033[0m\n' "$*"; }
error()  { printf '\033[1;31m[x] %s\033[0m\n' "$*" >&2; exit 1; }

[[ "$(id -u)" -eq 0 ]] || error "Ejecútalo como root."

ip_publica() {
    ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}'
}

mostrar_estado() {
    local pub; pub=$(ip_publica)
    echo
    echo "  DNS para el router:   ${pub}"
    echo "  DNS secundario:       85.208.114.52   (xdp.es, por si tu IP cambia)"
    echo
    echo "  Enlace para autorizar tu IP (ábrelo desde casa, con el wifi de casa):"
    echo "    http://${pub}:${AUTH_PORT}/autorizar?clave=$(cat "$CLAVE_F")"
    echo
    echo "  IPs autorizadas ahora:"
    sed 's/^/    /' "$LISTA" 2>/dev/null || true
    echo
}

if [[ "${1:-}" == "--estado" ]]; then
    [[ -f "$CLAVE_F" ]] || error "Aún no está instalado."
    mostrar_estado; exit 0
fi
if [[ "${1:-}" == "--autorizar" ]]; then
    [[ -x "$AUTH_PY" ]] || error "Aún no está instalado."
    python3 -I "$AUTH_PY" --anadir "${2:?Falta la IP}"
    mostrar_estado; exit 0
fi

# --- Comprobaciones -----------------------------------------------------------
[[ -f /etc/blocky/config.yml ]] || error "Primero ejecuta instalar-dns-evade.sh."
command -v nft >/dev/null || apt-get install -y -q nftables
PUB=$(ip_publica)
[[ -n "$PUB" ]] || error "No encuentro la IP pública del VPS."
if ss -lnup | grep -E "[[:space:]]${PUB//./\\.}:53[[:space:]]|[[:space:]]0\.0\.0\.0:53[[:space:]]|\*:53[[:space:]]" | grep -qv blocky; then
    error "Otro programa ya escucha en el puerto 53 de ${PUB}: $(ss -lnup | grep ':53 ' | head -3)"
fi

# IP de tu casa = la de esta sesión SSH
IP_CASA="${SSH_CLIENT:-}"; IP_CASA="${IP_CASA%% *}"
[[ -z "$IP_CASA" ]] && IP_CASA=$(who -m 2>/dev/null | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | head -1 || true)

mkdir -p "$CONF_DIR"; chmod 700 "$CONF_DIR"
touch "$LISTA"
[[ -s "$CLAVE_F" ]] || { head -c 18 /dev/urandom | base64 | tr '+/' '-_' > "$CLAVE_F"; chmod 600 "$CLAVE_F"; }

# --- 1. Firewall: puerto 53 solo para IPs autorizadas -------------------------
verde "1/4 Firewall del puerto 53"
cat > "$FW" <<'EOF'
#!/usr/bin/env bash
# Regenera la tabla nftables del DNS: el puerto 53 solo para las IPs de la lista.
set -euo pipefail
LISTA=/etc/dns-evade/permitidos.txt
nft -f - <<'NFT'
table inet dns_evade
delete table inet dns_evade
table inet dns_evade {
    set permitidos { type ipv4_addr; }
    chain entrada {
        type filter hook input priority -5; policy accept;
        iif "lo" accept
        udp dport 53 ip saddr @permitidos accept
        tcp dport 53 ip saddr @permitidos accept
        udp dport 53 drop
        tcp dport 53 drop
    }
}
NFT
while read -r ip; do
    [[ "$ip" =~ ^[0-9.]+$ ]] && nft add element inet dns_evade permitidos "{ $ip }"
done < "$LISTA"
EOF
chmod 0755 "$FW"

cat > /etc/systemd/system/dns-evade-fw.service <<EOF
[Unit]
Description=Firewall del DNS: puerto 53 solo para IPs autorizadas
Before=blocky.service
After=network-pre.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=$FW

[Install]
WantedBy=multi-user.target
EOF

# --- 2. Autorizador por enlace secreto ----------------------------------------
verde "2/4 Enlace para autorizar tu IP (puerto ${AUTH_PORT})"
cat > "$AUTH_PY" <<EOF
#!/usr/bin/env python3
"""Autoriza en el DNS la IP que abre el enlace secreto (o la que se pasa con --anadir)."""
import hmac, ipaddress, subprocess, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

LISTA = "$LISTA"
CLAVE = open("$CLAVE_F").read().strip()
FW = "$FW"
MAX_IPS = $MAX_IPS

def anadir(ip):
    ip = str(ipaddress.IPv4Address(ip))
    try:
        ips = [l.strip() for l in open(LISTA) if l.strip()]
    except FileNotFoundError:
        ips = []
    ips = [x for x in ips if x != ip] + [ip]
    ips = ips[-MAX_IPS:]
    with open(LISTA, "w") as f:
        f.write("\n".join(ips) + "\n")
    subprocess.run([FW], check=True)
    return ip

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def responder(self, codigo, texto):
        cuerpo = ("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
                  "<body style='font-family:sans-serif;padding:2em;font-size:1.3em'>" + texto).encode()
        self.send_response(codigo)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def do_GET(self):
        u = urlparse(self.path)
        clave = parse_qs(u.query).get("clave", [""])[0]
        if u.path != "/autorizar" or not hmac.compare_digest(clave, CLAVE):
            time.sleep(1)
            return self.responder(404, "No encontrado")
        try:
            ip = anadir(self.client_address[0])
        except ValueError:
            return self.responder(400, "Solo se pueden autorizar IPs IPv4. Abre el enlace con IPv6 desactivado o desde el PC.")
        self.responder(200, "&#9989; IP <b>%s</b> autorizada.<br>Ya puedes usar el DNS del VPS en esta red." % ip)

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--anadir":
        print("Autorizada:", anadir(sys.argv[2]))
    else:
        ThreadingHTTPServer(("0.0.0.0", $AUTH_PORT), H).serve_forever()
EOF
chmod 0700 "$AUTH_PY"

cat > /etc/systemd/system/dns-evade-autorizar.service <<EOF
[Unit]
Description=Enlace para autorizar IPs en el DNS
After=network.target dns-evade-fw.service

[Service]
ExecStart=/usr/bin/python3 -I $AUTH_PY
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

# --- 3. Blocky también en el puerto 53 de la IP pública -----------------------
verde "3/4 Blocky escuchando en ${PUB}:53"
python3 -I - "$PUB" <<'EOF'
import re, sys
pub = sys.argv[1]
p = "/etc/blocky/config.yml"
s = open(p).read()
s = re.sub(r"(?m)^(\s*dns:\s*).*$", lambda m: f"{m.group(1)}127.0.0.1:5354,{pub}:53", s, count=1)
open(p, "w").write(s)
EOF
mkdir -p /etc/systemd/system/blocky.service.d
cat > /etc/systemd/system/blocky.service.d/puerto53.conf <<'EOF'
[Unit]
After=dns-evade-fw.service
Wants=dns-evade-fw.service

[Service]
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
EOF

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
    ufw allow 53 >/dev/null
    ufw allow ${AUTH_PORT}/tcp >/dev/null
    echo "ufw: abiertos 53 y ${AUTH_PORT} (el 53 sigue filtrado por IP)"
fi

# --- 4. Arrancar y autorizar tu IP actual -------------------------------------
verde "4/4 Arrancando"
systemctl daemon-reload
systemctl enable --now dns-evade-fw.service >/dev/null 2>&1
systemctl restart dns-evade-fw.service
if [[ -n "$IP_CASA" ]] && [[ "$IP_CASA" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    python3 -I "$AUTH_PY" --anadir "$IP_CASA"
else
    aviso "No he detectado la IP de tu casa: usa el enlace de abajo desde casa."
fi
systemctl enable dns-evade-autorizar.service >/dev/null 2>&1
systemctl restart dns-evade-autorizar.service
systemctl restart blocky
sleep 3

dig @"$PUB" -p 53 example.com +short +time=3 | grep -q . \
    || error "Blocky no responde en ${PUB}:53 (mira: journalctl -u blocky -n 30)."
echo "DNS en ${PUB}:53 OK"

mostrar_estado
cat <<EOF
  Guarda el enlace en favoritos del móvil. Si un día el DNS deja de ir,
  ábrelo conectado al wifi de casa y vuelve a funcionar.
  Para volver a verlo:  bash dns-puerto53.sh --estado
EOF
