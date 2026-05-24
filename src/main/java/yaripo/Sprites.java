package yaripo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprite loader & geometric placeholder renderer.
 */
public class Sprites {

    private final Map<String, BufferedImage> cache = new HashMap<>();

    private static final String[] SPRITE_KEYS = {
        "player_right", "player_left",
        "tile_solid", "tile_hook", "tile_water", "tile_acid",
        "item_hook", "item_pickaxe", "item_boots", "hook_head",
        "aruana_idle", "aruana_walk", "aruana_attack", "aruana_win", "aruana_portrait",
        "iara_idle", "iara_swim", "iara_jump", "iara_shy", "iara_victory",
        "iara_portrait_neutral", "iara_portrait_happy", "iara_portrait_sad"
    };

    public Sprites() {
        for (String key : SPRITE_KEYS) {
            String path = "sprites/" + key + ".png";
            // Map keys to actual file paths if they differ
            path = switch (key) {
                case "aruana_idle" -> "sprites/aruana/idle_frente.png";
                case "aruana_walk" -> "sprites/aruana/walk.png";
                case "aruana_attack" -> "sprites/aruana/arco_ataque.png";
                case "aruana_win" -> "sprites/aruana/comemorando.png";
                case "aruana_portrait" -> "sprites/aruana/retrato.png";
                case "iara_idle" -> "sprites/iara/idle_frente.png";
                case "iara_swim" -> "sprites/iara/nado_lateral.png";
                case "iara_jump" -> "sprites/iara/salto_cauda.png";
                case "iara_shy" -> "sprites/iara/timida_coracao.png";
                case "iara_victory" -> "sprites/iara/vitoria_regia_aceno.png";
                case "iara_portrait_neutral" -> "sprites/iara/retrato_neutro.png";
                case "iara_portrait_happy" -> "sprites/iara/retrato_feliz.png";
                case "iara_portrait_sad" -> "sprites/iara/retrato_triste.png";
                default -> path;
            };
            loadSprite(key, path);
        }
    }

    public void loadSprite(String key, String path) {
        try {
            File f = new File(path);
            if (f.exists()) cache.put(key, ImageIO.read(f));
        } catch (Exception ignored) {}
    }

    private boolean has(String key) { return cache.containsKey(key); }

    // ─── Player ───────────────────────────────────────────────────────────────

    public void drawPlayer(Graphics2D g, float px, float py, boolean facingRight,
                            String state, int walkTick) {
        int pw = Player.W, ph = Player.H;
        int x = Math.round(px) - pw / 2;
        int y = Math.round(py) - ph;

        // Se houver translação/escala aplicada no Graphics2D (como no epílogo),
        // px e py podem ser 0, então o arredondamento funciona.
        // O x centralizado em px e y na base em py é preservado.

        BufferedImage img = null;
        if ("dead".equals(state)) {
            // Aruana deitada ou algo assim? Usaremos portrait ou idle por enquanto
            img = cache.get("aruana_portrait");
        } else if ("walk".equals(state)) {
            img = cache.get("aruana_walk");
        } else if ("win".equals(state)) {
            img = cache.get("aruana_win");
        } else if ("attack".equals(state)) {
            img = cache.get("aruana_attack");
        } else {
            img = cache.get("aruana_idle");
        }

        if (img != null) {
            if (!facingRight) {
                g.drawImage(img, x + pw, y, -pw, ph, null);
            } else {
                g.drawImage(img, x, y, pw, ph, null);
            }
            return;
        }

        // Body bob when walking
        int bob = 0;
        if ("walk".equals(state)) bob = ((walkTick / 5) % 2 == 0) ? 0 : -1;

        Color bodyColor = switch (state) {
            case "dead"  -> new Color(160, 60, 60);
            case "acid"  -> new Color(180, 220, 60);
            case "swing" -> new Color(140, 200, 255);
            default      -> new Color(200, 160, 100);
        };

        // Hair flow (back layer)
        g.setColor(new Color(40, 25, 18));
        int hairOffset = (int)(Math.sin(walkTick * 0.15) * 1.5);
        if (facingRight) {
            g.fillRoundRect(x - 2, y + 4 + bob, 6, 12 + hairOffset, 4, 4);
        } else {
            g.fillRoundRect(x + pw - 4, y + 4 + bob, 6, 12 + hairOffset, 4, 4);
        }

        // Body
        g.setColor(bodyColor);
        g.fillRoundRect(x + 3, y + 10 + bob, pw - 6, ph - 14, 4, 4);

        // Head
        g.setColor(new Color(220, 180, 130));
        g.fillOval(x + 4, y + bob, pw - 8, 12);

        // Eye
        g.setColor(Color.BLACK);
        if (facingRight) g.fillOval(x + pw - 8, y + 4 + bob, 2, 2);
        else             g.fillOval(x + 6, y + 4 + bob, 2, 2);

        // Headband stripe
        g.setColor(new Color(180, 60, 40));
        g.fillRect(x + 4, y + 6 + bob, pw - 8, 2);

        // Legs
        g.setColor(bodyColor.darker());
        int legPhase = "walk".equals(state) ? (walkTick / 5) % 2 : 0;
        int lOff = legPhase == 0 ? 0 : 2;
        g.fillRect(x + 4, y + ph - 5 + bob, 4, 5 - lOff);
        g.fillRect(x + pw - 8, y + ph - 5 + bob, 4, 5 - (2 - lOff));
    }

    // ─── Tiles ────────────────────────────────────────────────────────────────

    public void drawTile(Graphics2D g, int px, int py, char type, long tick) {
        int s = TileMap.TILE;
        switch (type) {
            case '#' -> drawSolid(g, px, py, s);
            case 'H' -> drawHookable(g, px, py, s, tick);
            case '~' -> drawWater(g, px, py, s, tick);
            case 'A' -> drawAcid(g, px, py, s, tick);
            case 'B' -> drawBreakable(g, px, py, s);
            case 'X' -> drawTrap(g, px, py, s);
            case '1' -> drawItemGlow(g, px, py, s, new Color(255, 200, 80), "item_hook", tick);
            case '2' -> drawItemGlow(g, px, py, s, new Color(180, 220, 255), "item_pickaxe", tick);
            case '3' -> drawItemGlow(g, px, py, s, new Color(80, 220, 120), "item_boots", tick);
            case 'E' -> drawExit(g, px, py, s, tick);
            default  -> {}
        }
    }

    private void drawSolid(Graphics2D g, int x, int y, int s) {
        if (has("tile_solid")) { g.drawImage(cache.get("tile_solid"), x, y, s, s, null); return; }
        // Base escura de pedra de caverna
        g.setColor(new Color(28, 20, 14));
        g.fillRect(x, y, s, s);
        // Variação de cor por posição (textura procedural)
        int hash = (x * 7 + y * 13) & 0xFF;
        int var = (hash % 12) - 6;
        g.setColor(new Color(
            Math.max(0, Math.min(255, 34 + var)),
            Math.max(0, Math.min(255, 24 + var / 2)),
            Math.max(0, Math.min(255, 16 + var / 3))
        ));
        // Padrão de pedra: blocos irregulares
        g.fillRect(x + 1, y + 1, s / 2 - 2, s / 2 - 2);
        g.fillRect(x + s / 2 + 1, y + s / 2 + 1, s / 2 - 2, s / 2 - 2);
        // Highlight sutil no topo (profundidade)
        g.setColor(new Color(50, 36, 24, 80));
        g.fillRect(x, y, s, 3);
        // Sombra na borda inferior/direita
        g.setColor(new Color(10, 7, 4, 120));
        g.fillRect(x, y + s - 2, s, 2);
        g.fillRect(x + s - 2, y, 2, s);
        // Grade de pedra
        g.setColor(new Color(15, 10, 6));
        g.drawRect(x, y, s - 1, s - 1);
        g.drawLine(x, y + s / 2, x + s, y + s / 2);
        g.drawLine(x + s / 2, y, x + s / 2, y + s);
    }

    private void drawHookable(Graphics2D g, int x, int y, int s, long tick) {
        if (has("tile_hook")) { g.drawImage(cache.get("tile_hook"), x, y, s, s, null); return; }
        g.setColor(new Color(50, 32, 18));
        g.fillRect(x, y, s, s);
        // Animated grip strip — color shifts slightly each frame
        int shimmer = (int)((Math.sin(tick * 0.08 + x * 0.05) + 1) * 20);
        g.setColor(new Color(120 + shimmer, 80 + shimmer / 2, 30));
        g.fillRect(x + 2, y + 2, s - 4, 6);
        g.setColor(new Color(28, 16, 8));
        for (int i = x + 4; i < x + s - 4; i += 5) {
            g.fillRect(i, y + 3, 2, 4);
        }
        g.setColor(new Color(90, 60, 35));
        g.drawRect(x, y, s - 1, s - 1);
    }

    private void drawWater(Graphics2D g, int x, int y, int s, long tick) {
        if (has("tile_water")) { g.drawImage(cache.get("tile_water"), x, y, s, s, null); return; }
        g.setColor(new Color(20, 60, 140, 220));
        g.fillRect(x, y, s, s);
        double wave = Math.sin(tick * 0.06 + x * 0.2) * 2.5;
        g.setColor(new Color(60, 120, 220, 120));
        g.fillRect(x, (int)(y + wave), s, s / 3);
        // Shimmer
        g.setColor(new Color(180, 220, 255, 80));
        int shimX = (int)(x + (tick * 2 + x) % s);
        g.drawLine(shimX, y + 4, shimX + 4, y + 4);
    }

    private void drawAcid(Graphics2D g, int x, int y, int s, long tick) {
        if (has("tile_acid")) { g.drawImage(cache.get("tile_acid"), x, y, s, s, null); return; }
        g.setColor(new Color(60, 180, 40, 230));
        g.fillRect(x, y, s, s);
        double wave = Math.sin(tick * 0.09 + x * 0.3) * 2.5;
        g.setColor(new Color(140, 255, 80, 140));
        g.fillRect(x, (int)(y + wave), s, s / 3);
        // Glow halo
        g.setColor(new Color(180, 255, 100, 50));
        g.fillRect(x - 1, y - 1, s + 2, s + 2);
        // Bubbles
        if ((tick + x) % 25 == 0) {
            g.setColor(new Color(220, 255, 140, 200));
            g.fillOval(x + (int)((tick * 3 + x) % (s - 6)), y + 4, 5, 5);
        }
        if ((tick + x + 13) % 35 == 0) {
            g.setColor(new Color(200, 255, 120, 160));
            g.fillOval(x + (int)((tick * 2 + x + 7) % (s - 4)), y + s / 2, 3, 3);
        }
    }

    private void drawBreakable(Graphics2D g, int x, int y, int s) {
        g.setColor(new Color(70, 55, 38));
        g.fillRect(x, y, s, s);
        g.setColor(new Color(30, 20, 10));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x + 6, y + 4, x + 14, y + 18);
        g.drawLine(x + 14, y + 18, x + 10, y + 28);
        g.drawLine(x + 18, y + 8, x + 26, y + 22);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(110, 88, 55));
        g.drawRect(x, y, s - 1, s - 1);
    }

    private void drawTrap(Graphics2D g, int x, int y, int s) {
        g.setColor(new Color(65, 46, 28));
        g.fillRect(x, y, s, s);
        g.setColor(new Color(85, 62, 38));
        g.drawRect(x, y, s - 1, s - 1);
    }

    private void drawItemGlow(Graphics2D g, int x, int y, int s, Color glow, String key, long tick) {
        float pulse = (float)(0.7 + 0.3 * Math.sin(tick * 0.08));
        RadialGradientPaint rp = new RadialGradientPaint(
            x + s / 2f, y + s / 2f, s * 0.9f,
            new float[]{0f, 1f},
            new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int)(140 * pulse)),
                        new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0)}
        );
        g.setPaint(rp);
        g.fillRect(x - 8, y - 8, s + 16, s + 16);
        g.setPaint(null);

        if (has(key)) { g.drawImage(cache.get(key), x + 4, y + 4, s - 8, s - 8, null); return; }

        g.setColor(glow);
        int cx = x + s / 2, cy = y + s / 2;
        if (key.equals("item_hook")) {
            g.setStroke(new BasicStroke(3f));
            g.drawArc(cx - 8, cy - 8, 16, 16, 0, 270);
            g.drawLine(cx + 8, cy, cx + 8, cy + 10);
            g.setStroke(new BasicStroke(1f));
        } else if (key.equals("item_pickaxe")) {
            g.setStroke(new BasicStroke(3f));
            g.drawLine(cx - 10, cy + 6, cx + 10, cy - 6);
            g.fillPolygon(new int[]{cx + 6, cx + 12, cx + 10}, new int[]{cy - 10, cy - 4, cy + 2}, 3);
            g.setStroke(new BasicStroke(1f));
        } else {
            g.fillRoundRect(cx - 8, cy - 3, 16, 10, 4, 4);
            g.fillRect(cx - 8, cy + 4, 10, 6);
        }
    }

    public void drawIara(Graphics2D g, int x, int y, int w, int h, String state, boolean facingRight) {
        String key = switch (state) {
            case "swim" -> "iara_swim";
            case "jump" -> "iara_jump";
            case "shy" -> "iara_shy";
            case "victory" -> "iara_victory";
            default -> "iara_idle";
        };
        BufferedImage img = cache.get(key);
        if (img != null) {
            if (!facingRight) {
                g.drawImage(img, x + w, y, -w, h, null);
            } else {
                g.drawImage(img, x, y, w, h, null);
            }
        } else {
            g.setColor(new Color(80, 180, 255));
            g.fillOval(x, y, w, h);
        }
    }

    public void drawPortrait(Graphics2D g, String who, int x, int y, int size) {
        String key = switch (who) {
            case "Aruana" -> "aruana_portrait";
            case "Iara", "Sweet Voice" -> "iara_portrait_neutral";
            case "Iara_Happy" -> "iara_portrait_happy";
            case "Iara_Sad" -> "iara_portrait_sad";
            default -> null;
        };
        if (key != null && has(key)) {
            g.drawImage(cache.get(key), x, y, size, size, null);
        }
    }

    private void drawExit(Graphics2D g, int x, int y, int s, long tick) {
        float pulse = (float)(0.6 + 0.4 * Math.sin(tick * 0.06));
        g.setColor(new Color(255, 230, 80, (int)(180 * pulse)));
        g.fillRect(x, y, s, s);
        g.setColor(new Color(80, 50, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("EXIT", x + 3, y + s - 8);
    }

    // ─── Hook head ────────────────────────────────────────────────────────────

    public void drawHookHead(Graphics2D g, float wx, float wy, boolean flying) {
        int hx = Math.round(wx) - 5;
        int hy = Math.round(wy) - 5;
        if (has("hook_head")) { g.drawImage(cache.get("hook_head"), hx, hy, 10, 10, null); return; }
        if (flying) {
            // Motion trail
            g.setColor(new Color(255, 220, 120, 100));
            g.fillOval(hx - 2, hy - 2, 14, 14);
        }
        g.setColor(new Color(200, 180, 80));
        g.fillOval(hx, hy, 10, 10);
        g.setColor(new Color(255, 220, 120));
        g.fillOval(hx + 3, hy + 3, 4, 4);
    }

    // ─── Aim guide ────────────────────────────────────────────────────────────

    public void drawAimGuide(Graphics2D g, float playerScreenX, float playerScreenY,
                              float mouseScreenX, float mouseScreenY,
                              boolean hookAvailable, boolean hookActive) {
        if (hookActive || !hookAvailable) return;
        float dist = (float) Math.hypot(mouseScreenX - playerScreenX, mouseScreenY - playerScreenY);
        if (dist < 20) return;

        g.setColor(new Color(255, 200, 80, 100));
        float[] dash = {6f, 6f};
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
        g.drawLine((int) playerScreenX, (int) playerScreenY, (int) mouseScreenX, (int) mouseScreenY);
        g.setStroke(new BasicStroke(1f));

        int mx = (int) mouseScreenX, my = (int) mouseScreenY;
        int r = 10;
        g.setColor(new Color(255, 220, 100, 220));
        g.drawOval(mx - r, my - r, r * 2, r * 2);
        g.drawLine(mx - r - 4, my, mx - r + 2, my);
        g.drawLine(mx + r - 2, my, mx + r + 4, my);
        g.drawLine(mx, my - r - 4, mx, my - r + 2);
        g.drawLine(mx, my + r - 2, mx, my + r + 4);
    }
}
