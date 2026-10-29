#!/usr/bin/env bash
# =============================================================================
#  dns-publico.sh
#  Abre el DNS anti-bloqueos del VPS a cualquiera en el puerto 53, para poder
#  poner la IP del VPS como DNS en el router o en cualquier dispositivo.
#  Requiere haber ejecutado antes instalar-dns-evade.sh.
#
#  Protecciones contra abusos (un DNS abierto se usa para ataques):
#    - Límite por IP de origen: 20 consultas/s (ráfagas de 60). Lo que pase
#      de ahí se descarta. Un hogar normal no llega ni de lejos.
#    - Consultas tipo ANY bloqueadas (las típicas de los ataques).
#
#  Uso (como root):    bash dns-publico.sh
#  Cambiar el límite:  LIMITE=40 RAFAGA=120 bash dns-publico.sh
#  Sin límite:         LIMITE=0 bash dns-publico.sh   (no recomendado)
#  Volver a cerrarlo:  bash dns-publico.sh --cerrar
# =============================================================================
set -euo pipefail

LIMITE="${LIMITE:-20}"     # consultas por segundo y por IP
RAFAGA="${RAFAGA:-60}"
FW=/usr/local/sbin/dns-publico-fw

verde()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
error()  { printf '\033[1;31m[x] %s\033[0m\n' "$*" >&2; exit 1; }

[[ "$(id -u)" -eq 0 ]] || error "Ejecútalo como root."
[[ -f /etc/blocky/config.yml ]] || error "Primero ejecuta instalar-dns-evade.sh."

PUB=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}')
[[ -n "$PUB" ]] || error "No encuentro la IP pública del VPS."

poner_puerto_blocky() {   # $1 = valor de ports.dns
    python3 -I - "$1" <<'EOF'
import re, sys
p = "/etc/blocky/config.yml"
s = open(p).read()
s = re.sub(r"(?m)^(\s*dns:\s*).*$", lambda m: m.group(1) + sys.argv[1], s, count=1)
open(p, "w").write(s)
EOF
}

# --- Cerrar ------------------------------------------------------------------
if [[ "${1:-}" == "--cerrar" ]]; then
    poner_puerto_blocky "127.0.0.1:5354"
    systemctl disable --now dns-publico-fw.service >/dev/null 2>&1 || true
    nft delete table inet dns_publico 2>/dev/null || true
    systemctl restart blocky
    echo "DNS público cerrado. El DoH (https://…/dns-query) sigue funcionando."
    exit 0
fi

command -v nft >/dev/null || apt-get install -y -q nftables
if ss -lnup | awk '{print $5, $NF}' | grep -E "^(${PUB//./\\.}|0\.0\.0\.0|\*):53 " | grep -qv blocky; then
    error "Otro programa ya escucha en el puerto 53: $(ss -lnup | grep ':53 ' | head -3)"
fi

# --- 1. Firewall con límite por IP -------------------------------------------
if [[ "$LIMITE" == "0" ]]; then
verde "1/3 SIN límite de consultas por IP"
cat > "$FW" <<'EOF'
#!/usr/bin/env bash
# Puerto 53 abierto a todos, SIN límite por IP (LIMITE=0): solo se quita la tabla del límite.
nft delete table inet dns_publico 2>/dev/null || true
EOF
else
verde "1/3 Límite de ${LIMITE} consultas/s por IP (ráfagas de ${RAFAGA})"
cat > "$FW" <<EOF
#!/usr/bin/env bash
# Puerto 53 abierto a todos, con límite de consultas por IP de origen.
set -euo pipefail
nft -f - <<'NFT'
table inet dns_publico
delete table inet dns_publico
table inet dns_publico {
    set limite_v4 { type ipv4_addr; size 65535; flags dynamic, timeout; timeout 1m; }
    set limite_v6 { type ipv6_addr; size 65535; flags dynamic, timeout; timeout 1m; }
    chain entrada {
        type filter hook input priority -5; policy accept;
        iif "lo" accept
        meta nfproto ipv4 meta l4proto { udp, tcp } th dport 53 update @limite_v4 { ip  saddr limit rate over ${LIMITE}/second burst ${RAFAGA} packets } drop
        meta nfproto ipv6 meta l4proto { udp, tcp } th dport 53 update @limite_v6 { ip6 saddr limit rate over ${LIMITE}/second burst ${RAFAGA} packets } drop
    }
}
NFT
EOF
fi
chmod 0755 "$FW"
"$FW"

cat > /etc/systemd/system/dns-publico-fw.service <<EOF
[Unit]
Description=Límite de consultas por IP en el DNS público
Before=blocky.service
After=network-pre.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=$FW

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable dns-publico-fw.service >/dev/null 2>&1

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
    ufw allow 53 >/dev/null
    echo "ufw: puerto 53 abierto"
fi

# --- 2. Blocky: puerto 53 público y sin consultas ANY -------------------------
verde "2/3 Blocky escuchando en ${PUB}:53"
poner_puerto_blocky "127.0.0.1:5354,${PUB}:53"
if ! grep -q '^filtering:' /etc/blocky/config.yml; then
    cat >> /etc/blocky/config.yml <<'EOF'
# Las consultas ANY se responden vacías (son las típicas de los ataques)
filtering:
  queryTypes:
    - ANY
EOF
fi
mkdir -p /etc/systemd/system/blocky.service.d
cat > /etc/systemd/system/blocky.service.d/puerto53.conf <<'EOF'
[Unit]
After=dns-publico-fw.service
Wants=dns-publico-fw.service

[Service]
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
EOF
systemctl daemon-reload
systemctl restart blocky
sleep 3

# --- 3. Prueba ---------------------------------------------------------------
verde "3/3 Comprobando"
dig @"$PUB" example.com +short +time=3 | grep -q . \
    || error "Blocky no responde en ${PUB}:53 (mira: journalctl -u blocky -n 30)."
echo "DNS público OK"

cat <<EOF

=====================================================================
 DNS para el router o cualquier dispositivo:   ${PUB}
 DNS secundario recomendado:                   85.208.114.52 (xdp.es)

 Comprueba desde fuera (PC de casa, en cmd):   nslookup example.com ${PUB}

 Si tu proveedor del VPS te avisa de abusos, ciérralo con:
     bash dns-publico.sh --cerrar
=====================================================================
EOF
