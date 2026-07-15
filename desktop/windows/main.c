/*
 * Bluke WiFi Receiver - native Windows client.
 *
 * Companion agent for the Bluke Android app's WiFi Remote module. Listens on
 * the LAN, advertises itself over mDNS so the phone auto-discovers it, and
 * injects the received keyboard / mouse input via the Win32 SendInput API.
 *
 * Zero runtime dependencies: compiles to a single self-contained .exe.
 *   gcc -O2 -o bluke_receiver.exe main.c -lws2_32 -luser32
 * (or just run build.bat)
 *
 * Wire protocol (matches app/.../network/WifiInputManager.kt):
 *   frame  = u16 big-endian length, then `length` bytes: [u8 type][payload]
 *   HELLO     0x01  client->server  [u8 protocolVersion][utf8 device name]
 *   AUTH      0x02  client->server  [utf8 PIN]
 *   AUTH_OK   0x03  server->client  [utf8 receiver name]
 *   AUTH_FAIL 0x04  server->client
 *   INPUT     0x10  client->server  [u8 reportId][HID report bytes]
 *   LED       0x20  server->client  [u8 led bitmask]   (not sent by v1)
 *   PING      0x30  client->server
 *   PONG      0x31  server->client
 *
 * INPUT payloads reuse Bluke's Bluetooth HID report formats:
 *   report 1 (keyboard): [modifiers][reserved][6 x key usage id]
 *   report 2 (mouse):    [buttons][dx][dy][wheel]   (signed deltas)
 *   report 3 (gamepad):  received but not injected (needs a virtual gamepad)
 */

#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* Link with: -lws2_32 -luser32 (see build.bat) */

#define TYPE_HELLO     0x01
#define TYPE_AUTH      0x02
#define TYPE_AUTH_OK   0x03
#define TYPE_AUTH_FAIL 0x04
#define TYPE_INPUT     0x10
#define TYPE_LED       0x20
#define TYPE_PING      0x30
#define TYPE_PONG      0x31

#define DEFAULT_PORT 9570
#define MDNS_ADDR "224.0.0.251"
#define MDNS_PORT 5353

static int    g_port = DEFAULT_PORT;
static char   g_pin[16] = "";
static char   g_receiverName[128] = "";
static char   g_hostLabel[64] = "bluke-pc";
static unsigned char g_localIp[4] = {127, 0, 0, 1};

/* ---------------------------------------------------------------------------
 * HID keyboard usage id -> Windows virtual-key code
 * ------------------------------------------------------------------------- */

static WORD hidToVk(unsigned char u) {
    if (u >= 0x04 && u <= 0x1D) return (WORD)('A' + (u - 0x04));       /* a-z */
    if (u >= 0x1E && u <= 0x26) return (WORD)('1' + (u - 0x1E));       /* 1-9 */
    if (u == 0x27) return '0';
    if (u >= 0x3A && u <= 0x45) return (WORD)(VK_F1 + (u - 0x3A));     /* F1-F12 */
    if (u >= 0x59 && u <= 0x61) return (WORD)(VK_NUMPAD1 + (u - 0x59));/* KP 1-9 */
    switch (u) {
        case 0x28: return VK_RETURN;
        case 0x29: return VK_ESCAPE;
        case 0x2A: return VK_BACK;
        case 0x2B: return VK_TAB;
        case 0x2C: return VK_SPACE;
        case 0x2D: return VK_OEM_MINUS;
        case 0x2E: return VK_OEM_PLUS;
        case 0x2F: return VK_OEM_4;   /* [ */
        case 0x30: return VK_OEM_6;   /* ] */
        case 0x31: return VK_OEM_5;   /* backslash */
        case 0x32: return VK_OEM_5;   /* non-US # */
        case 0x33: return VK_OEM_1;   /* ; */
        case 0x34: return VK_OEM_7;   /* ' */
        case 0x35: return VK_OEM_3;   /* ` */
        case 0x36: return VK_OEM_COMMA;
        case 0x37: return VK_OEM_PERIOD;
        case 0x38: return VK_OEM_2;   /* / */
        case 0x39: return VK_CAPITAL;
        case 0x46: return VK_SNAPSHOT;
        case 0x47: return VK_SCROLL;
        case 0x48: return VK_PAUSE;
        case 0x49: return VK_INSERT;
        case 0x4A: return VK_HOME;
        case 0x4B: return VK_PRIOR;
        case 0x4C: return VK_DELETE;
        case 0x4D: return VK_END;
        case 0x4E: return VK_NEXT;
        case 0x4F: return VK_RIGHT;
        case 0x50: return VK_LEFT;
        case 0x51: return VK_DOWN;
        case 0x52: return VK_UP;
        case 0x53: return VK_NUMLOCK;
        case 0x54: return VK_DIVIDE;
        case 0x55: return VK_MULTIPLY;
        case 0x56: return VK_SUBTRACT;
        case 0x57: return VK_ADD;
        case 0x58: return VK_RETURN;  /* KP Enter */
        case 0x62: return VK_NUMPAD0;
        case 0x63: return VK_DECIMAL;
        case 0x65: return VK_APPS;
    }
    return 0;
}

/* modifier byte bit index -> virtual-key code */
static const WORD kModifierVk[8] = {
    VK_LCONTROL, VK_LSHIFT, VK_LMENU, VK_LWIN,
    VK_RCONTROL, VK_RSHIFT, VK_RMENU, VK_RWIN
};

static BOOL vkIsExtended(WORD vk) {
    switch (vk) {
        case VK_RCONTROL: case VK_RMENU:
        case VK_INSERT: case VK_DELETE: case VK_HOME: case VK_END:
        case VK_PRIOR: case VK_NEXT:
        case VK_LEFT: case VK_UP: case VK_RIGHT: case VK_DOWN:
        case VK_NUMLOCK: case VK_DIVIDE: case VK_SNAPSHOT:
        case VK_LWIN: case VK_RWIN: case VK_APPS:
            return TRUE;
        default:
            return FALSE;
    }
}

static void sendKeyVk(WORD vk, BOOL up) {
    if (vk == 0) return;
    INPUT in;
    memset(&in, 0, sizeof(in));
    in.type = INPUT_KEYBOARD;
    in.ki.wVk = vk;
    in.ki.dwFlags = up ? KEYEVENTF_KEYUP : 0;
    if (vkIsExtended(vk)) in.ki.dwFlags |= KEYEVENTF_EXTENDEDKEY;
    SendInput(1, &in, sizeof(INPUT));
}

static void sendMouse(DWORD flags, LONG dx, LONG dy, DWORD data) {
    INPUT in;
    memset(&in, 0, sizeof(in));
    in.type = INPUT_MOUSE;
    in.mi.dx = dx;
    in.mi.dy = dy;
    in.mi.mouseData = data;
    in.mi.dwFlags = flags;
    SendInput(1, &in, sizeof(INPUT));
}

/* ---------------------------------------------------------------------------
 * Input state (so a dropped link can release everything it pressed)
 * ------------------------------------------------------------------------- */

typedef struct {
    unsigned char prevMods;
    unsigned char prevKeys[6];
    unsigned char prevButtons;
} InjectorState;

static void injectKeyboard(InjectorState *st, const unsigned char *d, int len) {
    if (len < 8) return;
    unsigned char mods = d[0];
    const unsigned char *keys = d + 2;   /* 6 usage ids */

    for (int bit = 0; bit < 8; bit++) {
        int mask = 1 << bit;
        int was = st->prevMods & mask, now = mods & mask;
        if (now && !was) sendKeyVk(kModifierVk[bit], FALSE);
        else if (was && !now) sendKeyVk(kModifierVk[bit], TRUE);
    }

    /* releases: previously-held usages no longer present */
    for (int i = 0; i < 6; i++) {
        unsigned char u = st->prevKeys[i];
        if (u == 0) continue;
        int stillDown = 0;
        for (int j = 0; j < 6; j++) if (keys[j] == u) { stillDown = 1; break; }
        if (!stillDown) sendKeyVk(hidToVk(u), TRUE);
    }
    /* presses: newly-present usages */
    for (int i = 0; i < 6; i++) {
        unsigned char u = keys[i];
        if (u == 0) continue;
        int wasDown = 0;
        for (int j = 0; j < 6; j++) if (st->prevKeys[j] == u) { wasDown = 1; break; }
        if (!wasDown) sendKeyVk(hidToVk(u), FALSE);
    }

    st->prevMods = mods;
    memcpy(st->prevKeys, keys, 6);
}

static void injectMouse(InjectorState *st, const unsigned char *d, int len) {
    if (len < 4) return;
    unsigned char buttons = d[0];
    signed char dx = (signed char)d[1];
    signed char dy = (signed char)d[2];
    signed char wheel = (signed char)d[3];

    if (dx || dy) sendMouse(MOUSEEVENTF_MOVE, dx, dy, 0);
    if (wheel)    sendMouse(MOUSEEVENTF_WHEEL, 0, 0, (DWORD)(wheel * WHEEL_DELTA));

    static const DWORD downF[3] = {MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_MIDDLEDOWN};
    static const DWORD upF[3]   = {MOUSEEVENTF_LEFTUP,   MOUSEEVENTF_RIGHTUP,   MOUSEEVENTF_MIDDLEUP};
    for (int bit = 0; bit < 3; bit++) {
        int mask = 1 << bit;
        int was = st->prevButtons & mask, now = buttons & mask;
        if (now && !was) sendMouse(downF[bit], 0, 0, 0);
        else if (was && !now) sendMouse(upF[bit], 0, 0, 0);
    }
    st->prevButtons = buttons;
}

static void releaseAll(InjectorState *st) {
    for (int bit = 0; bit < 8; bit++)
        if (st->prevMods & (1 << bit)) sendKeyVk(kModifierVk[bit], TRUE);
    for (int i = 0; i < 6; i++)
        if (st->prevKeys[i]) sendKeyVk(hidToVk(st->prevKeys[i]), TRUE);
    static const DWORD upF[3] = {MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_MIDDLEUP};
    for (int bit = 0; bit < 3; bit++)
        if (st->prevButtons & (1 << bit)) sendMouse(upF[bit], 0, 0, 0);
    memset(st, 0, sizeof(*st));
}

/* ---------------------------------------------------------------------------
 * Framed TCP protocol
 * ------------------------------------------------------------------------- */

static int recvExact(SOCKET s, unsigned char *buf, int n) {
    int got = 0;
    while (got < n) {
        int r = recv(s, (char *)buf + got, n - got, 0);
        if (r <= 0) return 0;
        got += r;
    }
    return 1;
}

static int sendFrame(SOCKET s, unsigned char type, const unsigned char *payload, int plen) {
    unsigned char header[3];
    int total = plen + 1;
    header[0] = (unsigned char)((total >> 8) & 0xFF);
    header[1] = (unsigned char)(total & 0xFF);
    header[2] = type;
    if (send(s, (char *)header, 3, 0) != 3) return 0;
    if (plen > 0 && send(s, (char *)payload, plen, 0) != plen) return 0;
    return 1;
}

static void handleClient(SOCKET client) {
    InjectorState st;
    memset(&st, 0, sizeof(st));
    int authed = 0;
    char deviceName[128] = "?";
    unsigned char frame[512];

    for (;;) {
        unsigned char hdr[2];
        if (!recvExact(client, hdr, 2)) break;
        int len = (hdr[0] << 8) | hdr[1];
        if (len < 1 || len > (int)sizeof(frame)) break;
        if (!recvExact(client, frame, len)) break;

        unsigned char type = frame[0];
        unsigned char *payload = frame + 1;
        int plen = len - 1;

        if (type == TYPE_HELLO) {
            if (plen >= 1) {
                int nameLen = plen - 1;
                if (nameLen > (int)sizeof(deviceName) - 1) nameLen = sizeof(deviceName) - 1;
                memcpy(deviceName, payload + 1, nameLen);
                deviceName[nameLen] = 0;
            }
            printf("    Hello from '%s'\n", deviceName);
            fflush(stdout);
        } else if (type == TYPE_AUTH) {
            char pin[32] = "";
            int n = plen < (int)sizeof(pin) - 1 ? plen : (int)sizeof(pin) - 1;
            memcpy(pin, payload, n);
            pin[n] = 0;
            if (strcmp(pin, g_pin) == 0) {
                authed = 1;
                sendFrame(client, TYPE_AUTH_OK, (unsigned char *)g_receiverName, (int)strlen(g_receiverName));
                printf("[+] '%s' authenticated. Receiving input.\n", deviceName);
            } else {
                sendFrame(client, TYPE_AUTH_FAIL, NULL, 0);
                printf("[!] Wrong PIN from '%s'. Closing.\n", deviceName);
                break;
            }
            fflush(stdout);
        } else if (type == TYPE_PING) {
            sendFrame(client, TYPE_PONG, NULL, 0);
        } else if (type == TYPE_INPUT) {
            if (authed && plen >= 1) {
                unsigned char reportId = payload[0];
                if (reportId == 1) injectKeyboard(&st, payload + 1, plen - 1);
                else if (reportId == 2) injectMouse(&st, payload + 1, plen - 1);
                /* reportId 3 (gamepad) requires a virtual gamepad driver; ignored */
            }
        }
        /* unknown frame types are ignored for forward compatibility */
    }

    releaseAll(&st);
    closesocket(client);
    printf("[-] '%s' disconnected.\n", deviceName);
    fflush(stdout);
}

/* ---------------------------------------------------------------------------
 * mDNS responder (DNS-SD) so the phone auto-discovers this receiver
 * ------------------------------------------------------------------------- */

static int writeName(unsigned char *buf, const char *const *labels, int count) {
    int pos = 0;
    for (int i = 0; i < count; i++) {
        int L = (int)strlen(labels[i]);
        buf[pos++] = (unsigned char)L;
        memcpy(buf + pos, labels[i], L);
        pos += L;
    }
    buf[pos++] = 0x00;
    return pos;
}

/* Build the full authoritative record set (PTR + SRV + TXT + A). */
static int buildMdnsPacket(unsigned char *out) {
    const char *svc[3]      = {"_bluke", "_tcp", "local"};
    const char *inst[4]     = {g_receiverName, "_bluke", "_tcp", "local"};
    const char *host[2]     = {g_hostLabel, "local"};
    int pos = 0;

    /* header: id=0, flags=0x8400 (QR|AA), QD=0, AN=4, NS=0, AR=0 */
    unsigned char header[12] = {0,0, 0x84,0x00, 0,0, 0,4, 0,0, 0,0};
    memcpy(out, header, 12);
    pos = 12;

    /* PTR: name=svc, TTL=120, rdata=inst */
    pos += writeName(out + pos, svc, 3);
    out[pos++] = 0x00; out[pos++] = 0x0C;          /* type PTR */
    out[pos++] = 0x00; out[pos++] = 0x01;          /* class IN (shared) */
    out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x78; /* TTL 120 */
    int rdlenPos = pos; pos += 2;
    int rdStart = pos;
    pos += writeName(out + pos, inst, 4);
    int rdlen = pos - rdStart;
    out[rdlenPos] = (rdlen >> 8) & 0xFF; out[rdlenPos + 1] = rdlen & 0xFF;

    /* SRV: name=inst, TTL=120, rdata=prio,weight,port,target(host) */
    pos += writeName(out + pos, inst, 4);
    out[pos++] = 0x00; out[pos++] = 0x21;          /* type SRV */
    out[pos++] = 0x80; out[pos++] = 0x01;          /* class IN | cache-flush */
    out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x78;
    rdlenPos = pos; pos += 2; rdStart = pos;
    out[pos++] = 0x00; out[pos++] = 0x00;          /* priority */
    out[pos++] = 0x00; out[pos++] = 0x00;          /* weight */
    out[pos++] = (g_port >> 8) & 0xFF; out[pos++] = g_port & 0xFF; /* port */
    pos += writeName(out + pos, host, 2);
    rdlen = pos - rdStart;
    out[rdlenPos] = (rdlen >> 8) & 0xFF; out[rdlenPos + 1] = rdlen & 0xFF;

    /* TXT: name=inst, rdata="ver=1" */
    pos += writeName(out + pos, inst, 4);
    out[pos++] = 0x00; out[pos++] = 0x10;          /* type TXT */
    out[pos++] = 0x80; out[pos++] = 0x01;
    out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x78;
    {
        const char *txt = "ver=1";
        int L = (int)strlen(txt);
        out[pos++] = 0x00; out[pos++] = (unsigned char)(L + 1);
        out[pos++] = (unsigned char)L;
        memcpy(out + pos, txt, L); pos += L;
    }

    /* A: name=host, rdata=IPv4 */
    pos += writeName(out + pos, host, 2);
    out[pos++] = 0x00; out[pos++] = 0x01;          /* type A */
    out[pos++] = 0x80; out[pos++] = 0x01;
    out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x00; out[pos++] = 0x78;
    out[pos++] = 0x00; out[pos++] = 0x04;
    memcpy(out + pos, g_localIp, 4); pos += 4;

    return pos;
}

/* Does the incoming query mention our service labels? (bytes: 06 _bluke 04 _tcp 05 local) */
static int mentionsService(const unsigned char *buf, int len) {
    static const unsigned char needle[] = {
        0x06,'_','b','l','u','k','e', 0x04,'_','t','c','p', 0x05,'l','o','c','a','l'
    };
    int nlen = (int)sizeof(needle);
    for (int i = 0; i + nlen <= len; i++)
        if (memcmp(buf + i, needle, nlen) == 0) return 1;
    return 0;
}

static DWORD WINAPI mdnsThread(LPVOID arg) {
    (void)arg;
    SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s == INVALID_SOCKET) return 0;

    BOOL reuse = TRUE;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char *)&reuse, sizeof(reuse));

    struct sockaddr_in local;
    memset(&local, 0, sizeof(local));
    local.sin_family = AF_INET;
    local.sin_addr.s_addr = INADDR_ANY;
    local.sin_port = htons(MDNS_PORT);
    if (bind(s, (struct sockaddr *)&local, sizeof(local)) == SOCKET_ERROR) {
        printf("[!] Could not bind mDNS port (auto-discovery disabled). Use manual IP.\n");
        fflush(stdout);
        closesocket(s);
        return 0;
    }

    struct ip_mreq mreq;
    mreq.imr_multiaddr.s_addr = inet_addr(MDNS_ADDR);
    mreq.imr_interface.s_addr = INADDR_ANY;
    setsockopt(s, IPPROTO_IP, IP_ADD_MEMBERSHIP, (char *)&mreq, sizeof(mreq));

    struct sockaddr_in mdns;
    memset(&mdns, 0, sizeof(mdns));
    mdns.sin_family = AF_INET;
    mdns.sin_addr.s_addr = inet_addr(MDNS_ADDR);
    mdns.sin_port = htons(MDNS_PORT);

    unsigned char packet[1024];
    int packetLen = buildMdnsPacket(packet);

    /* announce immediately, then respond to queries + re-announce periodically */
    sendto(s, (char *)packet, packetLen, 0, (struct sockaddr *)&mdns, sizeof(mdns));

    DWORD lastAnnounce = GetTickCount();
    for (;;) {
        fd_set rf;
        FD_ZERO(&rf);
        FD_SET(s, &rf);
        struct timeval tv = {1, 0};
        int r = select(0, &rf, NULL, NULL, &tv);
        if (r > 0 && FD_ISSET(s, &rf)) {
            unsigned char buf[2048];
            int n = recvfrom(s, (char *)buf, sizeof(buf), 0, NULL, NULL);
            /* respond to queries (QR bit clear) that mention our service */
            if (n > 12 && !(buf[2] & 0x80) && mentionsService(buf, n)) {
                sendto(s, (char *)packet, packetLen, 0, (struct sockaddr *)&mdns, sizeof(mdns));
            }
        }
        if (GetTickCount() - lastAnnounce > 10000) {
            sendto(s, (char *)packet, packetLen, 0, (struct sockaddr *)&mdns, sizeof(mdns));
            lastAnnounce = GetTickCount();
        }
    }
    /* not reached */
}

/* ---------------------------------------------------------------------------
 * Setup helpers
 * ------------------------------------------------------------------------- */

static void detectLocalIp(void) {
    SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s == INVALID_SOCKET) return;
    struct sockaddr_in probe;
    memset(&probe, 0, sizeof(probe));
    probe.sin_family = AF_INET;
    probe.sin_addr.s_addr = inet_addr("8.8.8.8");
    probe.sin_port = htons(80);
    if (connect(s, (struct sockaddr *)&probe, sizeof(probe)) == 0) {
        struct sockaddr_in name;
        int nlen = sizeof(name);
        if (getsockname(s, (struct sockaddr *)&name, &nlen) == 0)
            memcpy(g_localIp, &name.sin_addr, 4);
    }
    closesocket(s);
}

static void sanitizeHostLabel(void) {
    char host[64] = "";
    DWORD sz = sizeof(host);
    if (GetComputerNameA(host, &sz) && host[0]) {
        int j = 0;
        for (int i = 0; host[i] && j < (int)sizeof(g_hostLabel) - 1; i++) {
            char c = host[i];
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '-')
                g_hostLabel[j++] = c;
        }
        g_hostLabel[j] = 0;
        if (j == 0) strcpy(g_hostLabel, "bluke-pc");
    }
}

int main(int argc, char **argv) {
    const char *fixedPin = NULL;
    const char *fixedName = NULL;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--port") == 0 && i + 1 < argc) g_port = atoi(argv[++i]);
        else if (strcmp(argv[i], "--pin") == 0 && i + 1 < argc) fixedPin = argv[++i];
        else if (strcmp(argv[i], "--name") == 0 && i + 1 < argc) fixedName = argv[++i];
        else if (strcmp(argv[i], "--help") == 0) {
            printf("Usage: bluke_receiver [--port N] [--pin XXXXXX] [--name \"My PC\"]\n");
            return 0;
        }
    }

    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        fprintf(stderr, "WSAStartup failed\n");
        return 1;
    }

    detectLocalIp();
    sanitizeHostLabel();

    if (fixedPin) {
        strncpy(g_pin, fixedPin, sizeof(g_pin) - 1);
    } else {
        srand((unsigned)(GetTickCount() ^ (unsigned)time(NULL)));
        sprintf(g_pin, "%06d", rand() % 1000000);
    }
    if (fixedName) {
        strncpy(g_receiverName, fixedName, sizeof(g_receiverName) - 1);
    } else {
        char host[64] = "PC";
        DWORD sz = sizeof(host);
        GetComputerNameA(host, &sz);
        sprintf(g_receiverName, "Bluke Receiver (%.100s)", host);
    }

    printf("====================================================\n");
    printf("  Bluke WiFi Receiver (Windows)\n");
    printf("  Address : %d.%d.%d.%d:%d\n",
           g_localIp[0], g_localIp[1], g_localIp[2], g_localIp[3], g_port);
    printf("  PIN     : %s\n", g_pin);
    printf("  Enter this PIN in the Bluke app (WiFi Remote).\n");
    printf("====================================================\n");
    fflush(stdout);

    CreateThread(NULL, 0, mdnsThread, NULL, 0, NULL);

    SOCKET server = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server == INVALID_SOCKET) {
        fprintf(stderr, "socket() failed\n");
        WSACleanup();
        return 1;
    }
    BOOL reuse = TRUE;
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, (char *)&reuse, sizeof(reuse));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((unsigned short)g_port);
    if (bind(server, (struct sockaddr *)&addr, sizeof(addr)) == SOCKET_ERROR) {
        fprintf(stderr, "bind() failed on port %d (in use?)\n", g_port);
        WSACleanup();
        return 1;
    }
    listen(server, 1);

    for (;;) {
        struct sockaddr_in caddr;
        int clen = sizeof(caddr);
        SOCKET client = accept(server, (struct sockaddr *)&caddr, &clen);
        if (client == INVALID_SOCKET) continue;

        BOOL nodelay = TRUE;
        setsockopt(client, IPPROTO_TCP, TCP_NODELAY, (char *)&nodelay, sizeof(nodelay));

        printf("[+] Connection from %s:%d\n",
               inet_ntoa(caddr.sin_addr), ntohs(caddr.sin_port));
        fflush(stdout);
        handleClient(client);   /* one client at a time */
    }

    closesocket(server);
    WSACleanup();
    return 0;
}
