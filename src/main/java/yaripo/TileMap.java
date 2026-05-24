package yaripo;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Horizontal tile map — 220 wide x 30 tall, 32px tiles.
 * Tile chars:
 *   .  air         #  solid          ~  water       H  hookable
 *   B  breakable   A  acid           X  trap floor
 *   1  hook item   2  pickaxe item   3  boots item
 *   I  Iara trig   C  cave trig      V  vision trig E  exit
 */
public class TileMap {

    public static final int TILE = 32;
    public static final int COLS = 220;
    public static final int ROWS = 30;

    public static final char T_AIR     = '.';
    public static final char T_SOLID   = '#';
    public static final char T_WATER   = '~';
    public static final char T_HOOK    = 'H';
    public static final char T_BREAK   = 'B';
    public static final char T_ACID    = 'A';
    public static final char T_TRAP    = 'X';
    public static final char T_ITOOL   = '1';
    public static final char T_IPICK   = '2';
    public static final char T_IBOOT   = '3';
    public static final char T_IARA    = 'I';
    public static final char T_CAVE    = 'C';
    public static final char T_VISION  = 'V';
    public static final char T_FATHER  = 'F';
    public static final char T_MINER   = 'M';
    public static final char T_RIVER   = 'R';
    public static final char T_EXIT    = 'E';

    public record Particle(float x, float y, float vx, float vy, int life, Color color) {}
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    private char[][] map;
    private final Set<Long> firedTriggers = new HashSet<>();
    private final Set<Long> brokenTiles   = new HashSet<>();

    public TileMap() { buildMap(); }

    // ─── Build ────────────────────────────────────────────────────────────────

    private void fillRect(int c0, int r0, int c1, int r1, char ch) {
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (c >= 0 && c < COLS && r >= 0 && r < ROWS)
                    map[r][c] = ch;
    }

    private void buildMap() {
        map = new char[ROWS][COLS];
        for (char[] row : map) Arrays.fill(row, T_AIR);

        // ── Global borders ──────────────────────────────────────────────────
        for (int c = 0; c < COLS; c++) { map[0][c] = T_SOLID; map[ROWS-1][c] = T_SOLID; }
        for (int r = 0; r < ROWS; r++) { map[r][0] = T_SOLID; map[r][COLS-1] = T_SOLID; }

        // ── ZONE 0 — Spawn / Despertar (cols 1-35) ─────────────────────────
        // Chão principal row 22, paredes laterais
        fillRect(1, 22, 35, 22, T_SOLID);
        fillRect(1, 23, 35, ROWS-2, T_SOLID);
        fillRect(1, 1, 1, 21, T_SOLID);
        // Teto row 2 (não hookável para evitar bugs)
        fillRect(2, 2, 34, 2, T_SOLID);
        // Lago de água cols 1-14 (cobre até o fundo)
        fillRect(1, 19, 14, ROWS-2, T_WATER);
        // Plataforma de spawn col 15-22 row 18 (jogador acorda aqui, acima da água)
        fillRect(15, 18, 22, 18, T_SOLID);
        // Plataforma mais alta col 23-30 row 15 (com gancho)
        fillRect(23, 15, 30, 15, T_SOLID);
        // Hook item na plataforma alta
        map[14][26] = T_ITOOL;
        // Trigger de despertar (perto do spawn)
        map[17][18] = T_CAVE;
        // Parede direita com abertura rows 12-18
        fillRect(35, 1, 35, 12, T_SOLID);
        fillRect(35, 19, 35, 21, T_SOLID);

        // ── ZONE 1 — Primeiro Ácido SEM botas (cols 36-75) ─────────────────
        // Chão row 22
        fillRect(36, 22, 75, 22, T_SOLID);
        fillRect(36, 23, 75, ROWS-2, T_SOLID);
        // Teto row 2
        fillRect(36, 2, 75, 2, T_SOLID);
        fillRect(36, 8, 75, 8, T_SOLID);
        // Ácido cols 36-74 (cobre até o fundo)
        fillRect(36, 20, 74, ROWS-2, T_ACID);
        // Plataformas hookáveis sobre o ácido (única forma de passar)
        fillRect(44, 16, 46, 16, T_HOOK);
        fillRect(52, 13, 54, 13, T_HOOK);
        fillRect(60, 16, 62, 16, T_HOOK);
        // Plataforma de chegada col 69-75 row 18
        fillRect(69, 18, 75, 18, T_SOLID);
        // River trigger
        map[12][55] = T_RIVER;
        // Parede direita com abertura rows 15-18
        fillRect(75, 1, 75, 14, T_SOLID);
        fillRect(75, 19, 75, 21, T_SOLID);

        // ── ZONE 2 — Botas (cols 76-110) ───────────────────────────────────
        // Chão row 22
        fillRect(76, 22, 110, 22, T_SOLID);
        fillRect(76, 23, 110, ROWS-2, T_SOLID);
        // Teto row 2
        fillRect(76, 2, 110, 2, T_SOLID);
        fillRect(76, 8, 110, 8, T_SOLID);
        // Plataforma com botas col 78-84 row 17
        fillRect(78, 17, 84, 17, T_SOLID);
        map[16][81] = T_IBOOT;
        // Segundo ácido cols 76-105 (cobre até o fundo)
        fillRect(76, 20, 105, ROWS-2, T_ACID);
        // Stepping stones (hookáveis) sobre ácido
        fillRect(90, 17, 92, 17, T_HOOK);
        fillRect(97, 15, 99, 15, T_HOOK);
        fillRect(103, 17, 105, 17, T_HOOK);
        // Plataforma de saída col 106-110 row 18
        fillRect(106, 18, 110, 18, T_SOLID);
        // Miner trigger (movido para fora do ácido para evitar buraco visual)
        map[19][95] = T_MINER;
        // Parede direita com abertura rows 15-18
        fillRect(110, 1, 110, 14, T_SOLID);
        fillRect(110, 19, 110, 21, T_SOLID);

        // ── ZONE 3 — Picareta + Iara (cols 111-155) ────────────────────────
        // Chão row 22
        fillRect(111, 22, 155, 22, T_SOLID);
        fillRect(111, 23, 155, ROWS-2, T_SOLID);
        // Teto row 2
        fillRect(111, 2, 155, 2, T_SOLID);
        fillRect(111, 8, 155, 8, T_SOLID);
        // Plataforma com picareta col 113-119 row 17
        fillRect(113, 17, 119, 17, T_SOLID);
        map[16][116] = T_IPICK;
        // Iara trigger
        map[21][135] = T_IARA;
        // Trigger de caverna extra
        map[21][120] = T_CAVE;
        // Trap floor cols 128-148 row 22
        fillRect(128, 22, 148, 22, T_TRAP);
        // Parede quebrável col 150 rows 10-21
        fillRect(150, 10, 150, 21, T_BREAK);
        // Father guilt trigger
        map[12][130] = T_FATHER;
        // Plataformas
        fillRect(122, 16, 126, 16, T_SOLID);
        fillRect(131, 13, 135, 13, T_SOLID);
        fillRect(140, 16, 144, 16, T_SOLID);
        fillRect(148, 18, 154, 18, T_SOLID);

        // ── ZONE 4 — Visão / Finale (cols 156-218) ─────────────────────────
        // Chão row 22
        fillRect(156, 22, 217, 22, T_SOLID);
        fillRect(156, 23, 217, ROWS-2, T_SOLID);
        // Teto row 2 (teto superior — não hookável por design, mas mantido como sólido)
        fillRect(156, 2, 217, 2, T_SOLID);
        // Teto row 4 apenas até col 188
        fillRect(156, 4, 188, 4, T_SOLID);
        // Teto sólido row 8 apenas até col 188 (não mais hookável)
        fillRect(156, 8, 188, 8, T_SOLID);
        // Ácido final cols 156-190 (cobre até o fundo)
        fillRect(156, 20, 190, ROWS-2, T_ACID);
        // Stepping stones hookáveis sobre o ácido
        fillRect(158, 18, 161, 18, T_HOOK);
        fillRect(167, 15, 170, 15, T_HOOK);
        fillRect(176, 18, 179, 18, T_HOOK);
        fillRect(183, 16, 187, 16, T_SOLID);
        // Vision trigger
        map[12][174] = T_VISION;
        // Plataformas ascendentes para saída (espaçadas para serem alcançáveis com pulo)
        fillRect(190, 18, 195, 18, T_SOLID);
        fillRect(198, 15, 203, 15, T_SOLID);
        fillRect(206, 12, 211, 12, T_SOLID);
        fillRect(213, 9, 217, 9, T_SOLID);
        // Exit tile na plataforma final — acessível ao caminhar sobre ela
        fillRect(214, 8, 216, 8, T_EXIT);

        // ── Re-enforce borders ───────────────────────────────────────────────
        for (int c = 0; c < COLS; c++) { map[0][c] = T_SOLID; map[ROWS-1][c] = T_SOLID; }
        for (int r = 0; r < ROWS; r++) { map[r][0] = T_SOLID; map[r][COLS-1] = T_SOLID; }
    }

    // ─── Access ───────────────────────────────────────────────────────────────

    public char get(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return T_SOLID;
        if (brokenTiles.contains(encode(col, row))) return T_AIR;
        return map[row][col];
    }

    public void set(int col, int row, char c) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return;
        map[row][col] = c;
    }

    private static long encode(int col, int row) { return (long) row * 10000 + col; }

    public boolean consumeTrigger(int col, int row) {
        long key = encode(col, row);
        if (firedTriggers.contains(key)) return false;
        char c = get(col, row);
        if (isTrigger(c)) {
            firedTriggers.add(key);
            return true;
        }
        return false;
    }

    public static boolean isTrigger(char c) {
        return c == T_IARA || c == T_CAVE || c == T_VISION || c == T_FATHER || c == T_MINER || c == T_RIVER;
    }

    private char getLiquidAt(int c, int r) {
        // Water Zone 0: cols 1-14, rows 19-28
        if (r >= 19 && r < ROWS - 1 && c >= 1 && c <= 14) return T_WATER;
        // Acid Zones: rows 20-28
        if (r >= 20 && r < ROWS - 1) {
            if (c >= 36 && c <= 74)  return T_ACID;
            if (c >= 76 && c <= 105) return T_ACID;
            if (c >= 156 && c <= 190) return T_ACID;
        }
        return T_AIR;
    }

    public boolean hasFired(int col, int row) {
        return firedTriggers.contains(encode(col, row));
    }

    public boolean breakTile(int col, int row) {
        if (get(col, row) == T_BREAK) {
            brokenTiles.add(encode(col, row));
            spawnBreakParticles(col * TILE + TILE / 2, row * TILE + TILE / 2);
            return true;
        }
        return false;
    }

    public boolean collapseTrapsNear(float wx, float wy) {
        int col = (int)(wx / TILE);
        int row = (int)(wy / TILE);
        boolean any = false;
        for (int dc = -3; dc <= 3; dc++) {
            for (int dr = -1; dr <= 2; dr++) {
                if (get(col + dc, row + dr) == T_TRAP) {
                    map[row + dr][col + dc] = T_AIR;
                    spawnBreakParticles((col + dc) * TILE + TILE / 2, (row + dr) * TILE);
                    any = true;
                }
            }
        }
        return any;
    }

    // ─── Collision ────────────────────────────────────────────────────────────

    public boolean isSolid(int col, int row) {
        char c = get(col, row);
        return c == T_SOLID || c == T_HOOK || c == T_TRAP || c == T_BREAK;
    }

    public boolean isWater(int col, int row) { return get(col, row) == T_WATER; }
    public boolean isAcid(int col, int row)  { return get(col, row) == T_ACID; }
    public boolean isHookable(int col, int row) {
        if (row <= 4) return false; // Rows 0-4 são topo do mapa — não hookáveis para evitar bugs
        return get(col, row) == T_HOOK;
    }

    public boolean rectSolid(float lx, float ty, float rw, float rh) {
        return rectSolid(lx, ty, rw, rh, false);
    }

    public boolean rectSolid(float lx, float ty, float rw, float rh, boolean treatAcidAsSolid) {
        int c0 = (int) Math.floor(lx / TILE);
        int c1 = (int) Math.floor((lx + rw - 1) / TILE);
        int r0 = (int) Math.floor(ty / TILE);
        int r1 = (int) Math.floor((ty + rh - 1) / TILE);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                if (isSolid(c, r)) return true;
                if (treatAcidAsSolid && isAcid(c, r)) return true;
            }
        return false;
    }

    public boolean rectAcid(float lx, float ty, float rw, float rh) {
        int c0 = (int) Math.floor(lx / TILE);
        int c1 = (int) Math.floor((lx + rw - 1) / TILE);
        int r0 = (int) Math.floor(ty / TILE);
        int r1 = (int) Math.floor((ty + rh - 1) / TILE);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (isAcid(c, r)) return true;
        return false;
    }

    public char tryPickup(float wx, float wy, float pickupRadiusPx) {
        int c0 = Math.max(0, (int)((wx - pickupRadiusPx) / TILE));
        int c1 = Math.min(COLS - 1, (int)((wx + pickupRadiusPx) / TILE));
        int r0 = Math.max(0, (int)((wy - pickupRadiusPx) / TILE));
        int r1 = Math.min(ROWS - 1, (int)((wy + pickupRadiusPx) / TILE));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                char tile = get(c, r);
                if (tile == T_ITOOL || tile == T_IPICK || tile == T_IBOOT) {
                    float tx = c * TILE + TILE / 2f;
                    float ty = r * TILE + TILE / 2f;
                    if (Math.hypot(wx - tx, wy - ty) <= pickupRadiusPx) {
                        map[r][c] = T_AIR;
                        spawnPickupParticles((int) tx, (int) ty, tile);
                        return tile;
                    }
                }
            }
        }
        return 0;
    }

    // ─── Particles ────────────────────────────────────────────────────────────

    private void spawnBreakParticles(int cx, int cy) {
        for (int i = 0; i < 14; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 6f;
            float vy = (rng.nextFloat() - 1.0f) * 5f;
            Color c  = new Color(80 + rng.nextInt(60), 55 + rng.nextInt(30), 20 + rng.nextInt(20));
            particles.add(new Particle(cx, cy, vx, vy, 30 + rng.nextInt(20), c));
        }
    }

    private void spawnPickupParticles(int cx, int cy, char itemType) {
        Color c = switch (itemType) {
            case T_ITOOL -> new Color(255, 210, 80);
            case T_IPICK -> new Color(150, 210, 255);
            case T_IBOOT -> new Color(80, 220, 120);
            default      -> Color.WHITE;
        };
        for (int i = 0; i < 18; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 4f;
            float vy = rng.nextFloat() * -5f;
            particles.add(new Particle(cx, cy, vx, vy, 40 + rng.nextInt(20), c));
        }
    }

    public void spawnSplash(float wx, float wy) {
        for (int i = 0; i < 8; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 3f;
            float vy = rng.nextFloat() * -3f;
            particles.add(new Particle(wx, wy, vx, vy, 20 + rng.nextInt(15),
                new Color(60, 120, 220, 180)));
        }
    }

    // ─── Update & draw ────────────────────────────────────────────────────────

    public void update() {
        List<Particle> updated = new ArrayList<>(particles.size());
        for (Particle p : particles) {
            int life = p.life() - 1;
            if (life <= 0) continue;
            updated.add(new Particle(p.x() + p.vx(), p.y() + p.vy() + 0.15f,
                p.vx() * 0.92f, p.vy() + 0.05f, life, p.color()));
        }
        particles.clear();
        particles.addAll(updated);
    }

    public void draw(Graphics2D g, float camX, float camY, int screenW, int screenH, Sprites sprites, long tick) {
        int startCol = Math.max(0, (int)(camX / TILE) - 1);
        int endCol   = Math.min(COLS, (int)((camX + screenW) / TILE) + 2);
        int startRow = Math.max(0, (int)(camY / TILE) - 1);
        int endRow   = Math.min(ROWS, (int)((camY + screenH) / TILE) + 2);

        for (int r = startRow; r < endRow; r++) {
            for (int c = startCol; c < endCol; c++) {
                char tile = get(c, r);
                char liquid = getLiquidAt(c, r);
                int px = (int)(c * TILE - camX);
                int py = (int)(r * TILE - camY);

                // Draw liquid background if applicable
                if (liquid != T_AIR) {
                    sprites.drawTile(g, px, py, liquid, tick);
                }

                // If it's air or trigger, we might have already drawn liquid, so skip further drawing
                if (tile == T_AIR || isTrigger(tile)) continue;
                // If it's the same as liquid, already drawn
                if (tile == liquid) continue;

                sprites.drawTile(g, px, py, tile, tick);
            }
        }

        for (Particle p : particles) {
            float alpha = Math.min(1f, p.life() / 20f);
            Color c = new Color(
                p.color().getRed(), p.color().getGreen(), p.color().getBlue(),
                (int)(alpha * p.color().getAlpha())
            );
            g.setColor(c);
            int px = (int)(p.x() - camX);
            int py = (int)(p.y() - camY);
            g.fillRect(px - 2, py - 2, 4, 4);
        }
    }

    /** Player spawn at col 17, row 18 (plataforma acima do lago, zona 0). */
    public float[] startPos() {
        return new float[]{ 17 * TILE + TILE / 2f, 18 * TILE - 1 };
    }
}
