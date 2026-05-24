package yaripo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.Random;

/**
 * Core game panel — 60 FPS Swing timer loop.
 * Sized to the actual screen resolution at construction time.
 */
public class Game extends JPanel implements KeyListener, MouseListener, MouseMotionListener {

    private static final String STATE_TITLE    = "title";
    private static final String STATE_CUTSCENE = "cutscene";
    private static final String STATE_PLAYING  = "playing";
    private static final String STATE_DIALOGUE = "dialogue";
    private static final String STATE_DEAD     = "dead";
    private static final String STATE_EPILOGUE = "epilogue";

    private final int SCREEN_W;
    private final int SCREEN_H;

    private String gameState = STATE_TITLE;

    private float camX, camY;

    private TileMap  tileMap;
    private Player   player;
    private Dialogue dialogue;
    private Audio    audio;
    private Sprites  sprites;

    private long tick;
    private int  deathTimer;
    private int  epilogueTimer;
    private static final int DEATH_DELAY = 150;
    private static final float ZOOM = 2.0f;

    private int mouseX, mouseY;

    private final float[] starX, starY, starBright;
    private static final int STAR_COUNT = 180;
    private final Random rng = new Random(42);

    private String pendingDialogue;
    private boolean iaraTrapActive;
    private int     iaraTrapTimer;
    private boolean exitReached;

    // Cutscene (mini-história antes do jogo)
    private int cutsceneSlide;
    private int cutsceneTimer;
    private static final int CUTSCENE_SLIDE_DURATION = 220; // ~3.6s por slide
    private static final String[][] CUTSCENE_SLIDES = {
        {"The Black River dies in silence. The Shaman sold our waters to the miners."},
        {"To hide his crime, he demanded a sacrifice to the gods. The lightning struck."},
        {"The village chose the traitor's daughter as an offering to the abyss."},
        {"Cast into the darkness, she did not die. The mountain now whispers revenge."}
    };

    public Game(int screenW, int screenH) {
        this.SCREEN_W = screenW;
        this.SCREEN_H = screenH;
        setPreferredSize(new Dimension(screenW, screenH));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        starX = new float[STAR_COUNT];
        starY = new float[STAR_COUNT];
        starBright = new float[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rng.nextFloat() * SCREEN_W;
            starY[i] = rng.nextFloat() * (SCREEN_H * 0.65f);
            starBright[i] = rng.nextFloat();
        }
    }

    public void startGame() {
        javax.swing.Timer loop = new javax.swing.Timer(1000 / 60, e -> {
            tick++;
            update();
            repaint();
        });
        loop.start();
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    private void initWorld() {
        tileMap  = new TileMap();
        sprites  = new Sprites();
        dialogue = new Dialogue();
        if (audio == null) {
            audio = new Audio();
            audio.startAmbient();
        }

        float[] sp = tileMap.startPos();
        player = new Player(sp[0], sp[1]);
        player.hasHook = true; // A Menina acorda com o gancho (cutscene menciona isso)
        // Apply one resolveY-equivalent: drop player onto solid ground
        for (int i = 0; i < 60; i++) {
            if (tileMap.rectSolid(player.x - Player.W / 2f, player.y + 0.5f, Player.W, 1)) break;
            player.y += 1;
        }

        camX = player.x - (SCREEN_W / ZOOM) / 2f;
        camY = player.y - (SCREEN_H / ZOOM) / 2f;
        clampCamera();

        iaraTrapActive = false;
        iaraTrapTimer = 0;
        exitReached = false;

        pendingDialogue = "INTRO";
        dialogue.show("INTRO");
        gameState = STATE_DIALOGUE;
    }

    private void respawnPlayer() {
        float[] sp = tileMap.startPos();
        player.respawn(sp[0], sp[1]);
        player.hasHook = true; // mantém gancho após respawn
        camX = player.x - (SCREEN_W / ZOOM) / 2f;
        camY = player.y - (SCREEN_H / ZOOM) / 2f;
        clampCamera();
        for (int i = 0; i < 60; i++) {
            if (tileMap.rectSolid(player.x - Player.W / 2f, player.y + 0.5f, Player.W, 1)) break;
            player.y += 1;
        }
        deathTimer = 0;
        pendingDialogue = "WAKEUP";
        dialogue.show("WAKEUP");
        gameState = STATE_DIALOGUE;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    private void update() {
        switch (gameState) {
            case STATE_TITLE    -> updateTitle();
            case STATE_CUTSCENE -> updateCutscene();
            case STATE_PLAYING  -> updatePlaying();
            case STATE_DIALOGUE -> {}
            case STATE_DEAD     -> updateDead();
            case STATE_EPILOGUE -> updateEpilogue();
        }
    }

    private void updateCutscene() {
        cutsceneTimer++;
        if (cutsceneTimer >= CUTSCENE_SLIDE_DURATION) {
            advanceCutscene();
        }
    }

    private void updateTitle() {
        for (int i = 0; i < STAR_COUNT; i++) {
            starBright[i] = (float)(0.4 + 0.6 * Math.sin(tick * 0.04 + i * 1.3));
        }
    }

    private void updatePlaying() {
        if (player.dead) {
            gameState = STATE_DEAD;
            deathTimer = DEATH_DELAY;
            audio.sfxDeath();
            return;
        }

        // Detect hook attach event by polling state transition via timer hint
        Player.HookState before = player.hookState;
        player.update(tileMap);
        Player.HookState after = player.hookState;
        if (before == Player.HookState.FIRED && after == Player.HookState.PULLING) {
            audio.sfxHookAttach();
        }
        if ((before == Player.HookState.PULLING || before == Player.HookState.FIRED)
                && after == Player.HookState.IDLE) {
            audio.sfxRelease();
        }

        tileMap.update();

        // Pickup dialogue
        if (player.justPickedUp != 0) {
            audio.sfxPickup();
            String key = switch (player.justPickedUp) {
                case '1' -> "HOOK_PICKUP";
                case '2' -> "PICK_PICKUP";
                case '3' -> "BOOT_PICKUP";
                default  -> null;
            };
            if (key != null) {
                pendingDialogue = key;
                dialogue.show(key);
                gameState = STATE_DIALOGUE;
                return;
            }
        }

        // Pickaxe
        if (player.keyPickaxe && player.hasPickaxe) {
            if (player.tryBreak(tileMap)) audio.sfxBreak();
        }

        // Camera centralizada no player (metade da viewport dividida pelo ZOOM)
        float targetCamX = player.x - (SCREEN_W / ZOOM) / 2f;
        float targetCamY = player.y - (SCREEN_H / ZOOM) / 2f;
        camX += (targetCamX - camX) * 0.35f;
        camY += (targetCamY - camY) * 0.35f;
        clampCamera();

        checkTriggers();

        if (iaraTrapActive) {
            iaraTrapTimer++;
            if (iaraTrapTimer >= 60) {
                tileMap.collapseTrapsNear(player.x, player.y);
                iaraTrapActive = false;
            }
        }
    }

    private void checkTriggers() {
        int col = player.getTileCol();
        int row = player.getTileRow();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int cc = col + dc, rr = row + dr;
                char tile = tileMap.get(cc, rr);
                if (tile != 'I' && tile != 'C' && tile != 'V' && tile != 'F' && tile != 'M' && tile != 'R') continue;
                if (!tileMap.consumeTrigger(cc, rr)) continue;

                switch (tile) {
                    case 'C' -> {
                        pendingDialogue = "CAVE_VOICE";
                        if (tick < 120) { // early wakeup voice if very near start
                            pendingDialogue = "CAVE_VOICE";
                        }
                        dialogue.show(pendingDialogue);
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                        audio.sfxCave();
                    }
                    case 'I' -> {
                        pendingDialogue = "IARA_VOICE";
                        dialogue.show("IARA_VOICE");
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                        audio.sfxIara();
                        iaraTrapActive = true;
                        iaraTrapTimer = 0;
                    }
                    case 'V' -> {
                        pendingDialogue = "VISION";
                        dialogue.show("VISION");
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                    }
                    case 'F' -> {
                        pendingDialogue = "FATHER_GUILT";
                        dialogue.show("FATHER_GUILT");
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                        audio.sfxCave();
                    }
                    case 'M' -> {
                        pendingDialogue = "MINER_CAMP";
                        dialogue.show("MINER_CAMP");
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                    }
                    case 'R' -> {
                        pendingDialogue = "RIVER_TEARS";
                        dialogue.show("RIVER_TEARS");
                        gameState = STATE_DIALOGUE;
                        if (player != null) player.releaseKeys();
                    }
                }
            }
        }

        if (!exitReached && tileMap.get(col, row) == 'E') {
            exitReached = true;
            pendingDialogue = "EXIT_REACHED";
            dialogue.show("EXIT_REACHED");
            gameState = STATE_DIALOGUE;
            if (player != null) player.releaseKeys();
        }
    }

    private void updateDead() {
        deathTimer--;
        tileMap.update();
        if (deathTimer <= 0) respawnPlayer();
    }

    private void updateEpilogue() { epilogueTimer++; }

    // ─── Camera ───────────────────────────────────────────────────────────────

    private void clampCamera() {
        int worldW = TileMap.COLS * TileMap.TILE;
        int worldH = TileMap.ROWS * TileMap.TILE;
        float viewW = SCREEN_W / ZOOM;
        float viewH = SCREEN_H / ZOOM;
        camX = Math.max(0, Math.min(camX, worldW - viewW));
        camY = Math.max(0, Math.min(camY, worldH - viewH));
        if (worldW < viewW) camX = 0;
        if (worldH < viewH) camY = 0;
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (gameState) {
            case STATE_TITLE    -> drawTitle(g2);
            case STATE_CUTSCENE -> drawCutscene(g2);
            case STATE_PLAYING  -> drawPlaying(g2);
            case STATE_DIALOGUE -> { drawPlaying(g2); drawDialogue(g2); }
            case STATE_DEAD     -> { drawPlaying(g2); drawDeathOverlay(g2); }
            case STATE_EPILOGUE -> drawEpilogue(g2);
        }
    }

    private void drawTitle(Graphics2D g) {
        GradientPaint sky = new GradientPaint(0, 0, new Color(5, 5, 20),
                                              0, SCREEN_H, new Color(10, 20, 45));
        g.setPaint(sky);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        for (int i = 0; i < STAR_COUNT; i++) {
            float b = Math.max(0f, Math.min(1f, starBright[i]));
            g.setColor(new Color(b, b, b));
            int s = b > 0.8f ? 2 : 1;
            g.fillRect((int) starX[i], (int) starY[i], s, s);
        }

        // Mountain
        int n = 12;
        int[] mx = new int[n + 2];
        int[] my = new int[n + 2];
        for (int i = 0; i < n; i++) {
            mx[i] = (int)((float) i / (n - 1) * SCREEN_W);
            my[i] = (int)(SCREEN_H * 0.55f + Math.sin(i * 1.3) * 80);
        }
        mx[n] = SCREEN_W; my[n] = SCREEN_H;
        mx[n + 1] = 0; my[n + 1] = SCREEN_H;
        g.setColor(new Color(15, 12, 22));
        g.fillPolygon(mx, my, mx.length);

        // Moon
        int moonX = (int)(SCREEN_W * 0.78f);
        g.setColor(new Color(240, 230, 200));
        g.fillOval(moonX, (int)(SCREEN_H * 0.12f), 80, 80);
        g.setColor(new Color(15, 15, 35));
        g.fillOval(moonX + 18, (int)(SCREEN_H * 0.11f), 80, 80);

        g.setFont(new Font("SansSerif", Font.BOLD, 120));
        FontMetrics fm = g.getFontMetrics();
        int titleX = (SCREEN_W - fm.stringWidth("YARIPO")) / 2;
        int titleY = (int)(SCREEN_H * 0.40f);
        g.setColor(new Color(80, 50, 0));
        g.drawString("YARIPO", titleX + 5, titleY + 5);
        g.setColor(new Color(255, 215, 60));
        g.drawString("YARIPO", titleX, titleY);

        g.setFont(new Font("SansSerif", Font.ITALIC, 22));
        g.setColor(new Color(180, 160, 120));
        String sub = "Truth sleeps in the deep";
        g.drawString(sub, (SCREEN_W - g.getFontMetrics().stringWidth(sub)) / 2, titleY + 50);

        float pulse = Math.max(0f, Math.min(1f, (float)(0.6 + 0.4 * Math.sin(tick * 0.06))));
        g.setColor(new Color(1f, 1f, 1f, pulse));
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        String prompt = "Press ENTER to begin";
        g.drawString(prompt, (SCREEN_W - g.getFontMetrics().stringWidth(prompt)) / 2, (int)(SCREEN_H * 0.65f));

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(120, 110, 90));
        String controls = "A/D Move  |  W/Space Jump  |  Click = Hook  |  R/F Release  |  E Pickaxe  |  ESC Exit";
        g.drawString(controls, (SCREEN_W - g.getFontMetrics().stringWidth(controls)) / 2, SCREEN_H - 30);
    }

    private void drawPlaying(Graphics2D g) {
        AffineTransform oldAt = g.getTransform();

        // Fundo de caverna: gradiente escuro de cima para baixo
        GradientPaint caveBg = new GradientPaint(
            0, 0, new Color(8, 5, 12),
            0, SCREEN_H, new Color(2, 1, 4));
        g.setPaint(caveBg);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        // Textura de fundo: linhas de rocha sutis
        g.setColor(new Color(14, 10, 18, 40));
        for (int ry = 0; ry < SCREEN_H; ry += 32) {
            g.drawLine(0, ry, SCREEN_W, ry);
        }
        for (int rx = 0; rx < SCREEN_W; rx += 32) {
            g.drawLine(rx, 0, rx, SCREEN_H);
        }

        // Apply zoom transform
        g.scale(ZOOM, ZOOM);

        tileMap.draw(g, camX, camY, (int)(SCREEN_W / ZOOM), (int)(SCREEN_H / ZOOM), sprites, tick);

        if (player != null && !player.dead) {
            float psx = player.x - camX;
            float psy = player.y - Player.H / 2f - camY;
            sprites.drawAimGuide(g, psx, psy, (int)(mouseX / ZOOM), (int)(mouseY / ZOOM),
                player.isHookAvailable(), player.isHookActive());
        }

        if (player != null) player.draw(g, camX, camY, sprites, tick);

        // Efeito de caverna escura com luz de tocha ao redor do jogador
        drawCaveLight(g);

        // Restore transform for HUD
        g.setTransform(oldAt);

        drawHUD(g);
    }

    private void drawCaveLight(Graphics2D g) {
        if (player == null) return;
        float px = player.x - camX;
        float py = player.y - Player.H / 2f - camY;

        // Raio da luz da tocha com leve flickering
        float flicker = (float)(Math.sin(tick * 0.17) * 0.04 + Math.sin(tick * 0.31) * 0.03);
        int torchRadius = (int)((SCREEN_W * 0.18f) * (1f + flicker)); // Reduced radius for zoom

        // Cria máscara de escuridão
        java.awt.image.BufferedImage darkness = new java.awt.image.BufferedImage(
            (int)(SCREEN_W / ZOOM), (int)(SCREEN_H / ZOOM), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = darkness.createGraphics();
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Preenche tudo de preto opaco
        dg.setColor(new Color(0, 0, 0, 220));
        dg.fillRect(0, 0, SCREEN_W, SCREEN_H);

        // Apaga o círculo de luz usando AlphaComposite
        dg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.DST_OUT));
        float[] fractions = {0f, 0.55f, 1f};
        Color[] colors = {
            new Color(0, 0, 0, 255),
            new Color(0, 0, 0, 180),
            new Color(0, 0, 0, 0)
        };
        java.awt.RadialGradientPaint light = new java.awt.RadialGradientPaint(
            px, py, torchRadius, fractions, colors);
        dg.setPaint(light);
        dg.fillOval((int)(px - torchRadius), (int)(py - torchRadius),
                    torchRadius * 2, torchRadius * 2);
        dg.dispose();

        g.drawImage(darkness, 0, 0, null);

        // Tinge levemente de laranja/âmbar a área iluminada (calor da tocha)
        java.awt.image.BufferedImage warmth = new java.awt.image.BufferedImage(
            (int)(SCREEN_W / ZOOM), (int)(SCREEN_H / ZOOM), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D wg = warmth.createGraphics();
        wg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float[] wFrac = {0f, 0.4f, 1f};
        Color[] wColors = {
            new Color(255, 140, 30, 55),
            new Color(255, 100, 10, 25),
            new Color(0, 0, 0, 0)
        };
        java.awt.RadialGradientPaint warm = new java.awt.RadialGradientPaint(
            px, py, torchRadius, wFrac, wColors);
        wg.setPaint(warm);
        wg.fillOval((int)(px - torchRadius), (int)(py - torchRadius),
                    torchRadius * 2, torchRadius * 2);
        wg.dispose();
        g.drawImage(warmth, 0, 0, null);
    }

    private void drawHUD(Graphics2D g) {
        // ── Barras HP e Stamina (canto inferior esquerdo) ──────────────────
        if (player != null) {
            int barW = 160, barH = 12, barX = 16, barY = SCREEN_H - 60;
            // HP
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 6, 6);
            g.setColor(new Color(180, 30, 30));
            g.fillRoundRect(barX, barY, (int)(barW * player.hp), barH, 4, 4);
            g.setColor(new Color(220, 60, 60));
            g.fillRoundRect(barX, barY, (int)(barW * player.hp * 0.6f), barH / 2, 4, 4);
            g.setColor(new Color(200, 80, 80));
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString("HP", barX + barW + 6, barY + barH - 1);
            // Stamina
            int stY = barY + barH + 8;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(barX - 2, stY - 2, barW + 4, barH + 4, 6, 6);
            g.setColor(new Color(30, 120, 200));
            g.fillRoundRect(barX, stY, (int)(barW * player.stamina), barH, 4, 4);
            g.setColor(new Color(80, 180, 255));
            g.fillRoundRect(barX, stY, (int)(barW * player.stamina * 0.6f), barH / 2, 4, 4);
            g.setColor(new Color(100, 160, 220));
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString("ST", barX + barW + 6, stY + barH - 1);
        }

        // ── Inventory top-left ──────────────────────────────────────────────
        int invX = 16, invY = 16, slot = 44, pad = 6;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(invX - 6, invY - 6, 3 * (slot + pad) + 8, slot + 12, 10, 10);

        drawInventorySlot(g, invX, invY, slot, player != null && player.hasHook,
                          new Color(255, 200, 80), "H");
        drawInventorySlot(g, invX + slot + pad, invY, slot,
                          player != null && player.hasPickaxe,
                          new Color(160, 210, 255), "P");
        drawInventorySlot(g, invX + 2 * (slot + pad), invY, slot,
                          player != null && player.hasBoots,
                          new Color(80, 220, 120), "B");

        // Controls top-right, PT + EN
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(160, 150, 130, 220));
        String[] ptHints = {
            "A/D — Mover",
            "W/Espaço — Pular",
            "Clique — Gancho",
            "R/F — Soltar",
            "E — Picareta"
        };
        String[] enHints = {
            "A/D — Move",
            "W/Space — Jump",
            "Click — Hook",
            "R/F — Release",
            "E — Pickaxe"
        };
        int rx = SCREEN_W - 220, ry = 20;
        for (int i = 0; i < ptHints.length; i++) {
            g.setColor(new Color(220, 200, 150, 220));
            g.drawString(ptHints[i], rx, ry + i * 26);
            g.setColor(new Color(140, 140, 140, 180));
            g.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g.drawString("[EN] " + enHints[i], rx, ry + i * 26 + 12);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        // Hook hint when fired/attached
        if (player != null && player.hookHintTimer > 0) {
            float a = Math.min(1f, player.hookHintTimer / 60f);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(new Color(255, 215, 60, (int)(220 * a)));
            String s = "R / F — Soltar  (R / F — Release)";
            g.drawString(s, (SCREEN_W - g.getFontMetrics().stringWidth(s)) / 2, SCREEN_H - 180);
        }

        if (player != null && player.inAcid && !player.hasBoots) {
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(new Color(80, 220, 40, 220));
            String s = "ÁCIDO! / ACID!";
            g.drawString(s, (SCREEN_W - g.getFontMetrics().stringWidth(s)) / 2, SCREEN_H - 60);
        }
    }

    private void drawInventorySlot(Graphics2D g, int x, int y, int size, boolean filled,
                                    Color col, String label) {
        g.setColor(filled ? col.darker().darker() : new Color(30, 28, 25));
        g.fillRoundRect(x, y, size, size, 8, 8);
        g.setColor(filled ? col : new Color(60, 55, 45));
        g.setStroke(new BasicStroke(filled ? 2.5f : 1.2f));
        g.drawRoundRect(x, y, size, size, 8, 8);
        g.setStroke(new BasicStroke(1f));
        if (filled) {
            g.setColor(col);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString(label, x + size / 2 - 6, y + size / 2 + 7);
        }
    }

    private void drawDialogue(Graphics2D g) {
        if (dialogue == null || !dialogue.isActive()) return;
        Dialogue.DlgLine line = dialogue.current();
        if (line == null) return;

        int boxW = (int)(SCREEN_W * 0.85f);
        int boxH = 160;
        int boxX = (SCREEN_W - boxW) / 2;
        int boxY = SCREEN_H - boxH - 100;

        g.setColor(new Color(8, 6, 18, 245));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 16, 16);
        g.setColor(line.color().darker());
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 16, 16);
        g.setStroke(new BasicStroke(1f));

        int pad = 24;
        int portraitSize = 120;
        int textX = boxX + pad;

        // Draw portrait if available
        String speaker = line.speakerEN();
        boolean hasPortrait = speaker.contains("Aruana") || speaker.contains("Iara") || speaker.contains("Sweet Voice");
        
        if (hasPortrait) {
            int px = boxX + pad;
            int py = boxY + (boxH - portraitSize) / 2;
            sprites.drawPortrait(g, speaker, px, py, portraitSize);
            textX = px + portraitSize + pad;
        }

        // Speaker (bold, colored)
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(line.color());
        g.drawString(speaker, textX, boxY + 40);

        // Text (white, 18)
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.setColor(new Color(240, 235, 220));
        drawWrappedText(g, line.textEN(), textX, boxY + 75, boxX + boxW - textX - pad, 26);

        g.setFont(new Font("SansSerif", Font.ITALIC, 12));
        g.setColor(new Color(160, 160, 140));
        String hint = "Enter / Click to continue";
        g.drawString(hint, boxX + boxW - pad - g.getFontMetrics().stringWidth(hint), boxY + boxH - 12);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
        FontMetrics fm = g.getFontMetrics();
        for (String paragraph : text.split("\n")) {
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                String trial = line.length() == 0 ? w : line + " " + w;
                if (fm.stringWidth(trial) > maxW) {
                    g.drawString(line.toString(), x, y);
                    y += lineH;
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(trial);
                }
            }
            if (line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += lineH;
            }
        }
    }

    private void drawCutscene(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        if (cutsceneSlide >= CUTSCENE_SLIDES.length) return;
        String[] slide = CUTSCENE_SLIDES[cutsceneSlide];

        // Fade in/out
        float fadeIn  = Math.min(1f, cutsceneTimer / 40f);
        float fadeOut = cutsceneTimer > CUTSCENE_SLIDE_DURATION - 40
                        ? 1f - (cutsceneTimer - (CUTSCENE_SLIDE_DURATION - 40)) / 40f
                        : 1f;
        float alpha = Math.max(0f, Math.min(1f, fadeIn * fadeOut));

        // Número do slide / total
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(100, 90, 70, (int)(180 * alpha)));
        String num = (cutsceneSlide + 1) + " / " + CUTSCENE_SLIDES.length;
        g.drawString(num, SCREEN_W - 60, SCREEN_H - 20);

        // Linha decorativa
        g.setColor(new Color(180, 140, 60, (int)(120 * alpha)));
        int lw = (int)(SCREEN_W * 0.5f);
        g.fillRect((SCREEN_W - lw) / 2, (int)(SCREEN_H * 0.42f), lw, 1);

        // Texto EN
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.setColor(new Color(240, 225, 190, (int)(230 * alpha)));
        drawCenteredWrapped(g, slide[0], SCREEN_W, (int)(SCREEN_H * 0.44f), 28, (int)(SCREEN_W * 0.75f), alpha);

        // Dica
        if (cutsceneTimer > 80) {
            float ha = Math.min(1f, (cutsceneTimer - 80) / 40f) * alpha;
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(new Color(140, 130, 110, (int)(160 * ha)));
            String hint = cutsceneSlide < CUTSCENE_SLIDES.length - 1
                ? "Enter / Click to continue"
                : "Enter / Click to play";
            g.drawString(hint, (SCREEN_W - g.getFontMetrics().stringWidth(hint)) / 2, SCREEN_H - 30);
        }
    }

    private void drawCenteredWrapped(Graphics2D g, String text, int screenW, int y, int fontSize, int maxW, float alpha) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String trial = cur.length() == 0 ? w : cur + " " + w;
            if (fm.stringWidth(trial) > maxW) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        int lineH = fm.getHeight() + 4;
        int totalH = lines.size() * lineH;
        int startY = y - totalH / 2;
        for (String line : lines) {
            int lx = (screenW - fm.stringWidth(line)) / 2;
            g.drawString(line, lx, startY);
            startY += lineH;
        }
    }

    private void advanceCutscene() {
        cutsceneSlide++;
        cutsceneTimer = 0;
        if (cutsceneSlide >= CUTSCENE_SLIDES.length) {
            initWorld();
            dialogue.show("WAKEUP");
            gameState = STATE_DIALOGUE;
        }
    }

    private void drawDeathOverlay(Graphics2D g) {
        float progress = 1f - (float) deathTimer / DEATH_DELAY;
        float alpha = Math.max(0f, Math.min(0.85f, progress));
        g.setColor(new Color(0.55f, 0f, 0f, alpha));
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        if (deathTimer < 100) {
            float ta = Math.max(0f, Math.min(1f, (DEATH_DELAY - 100 - deathTimer) / 30f + 1f));
            g.setFont(new Font("SansSerif", Font.BOLD, 40));
            g.setColor(new Color(1f, 0.8f, 0.8f, ta));
            String msg = "Aruana has fallen...";
            g.drawString(msg, (SCREEN_W - g.getFontMetrics().stringWidth(msg)) / 2, SCREEN_H / 2 - 20);
        }
    }

    private void drawEpilogue(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        float t = Math.min(1f, epilogueTimer / 120f);

        // Warm Amazonian Sunrise
        GradientPaint sky = new GradientPaint(0, 0, new Color(15, 10, 25),
                                              0, SCREEN_H, new Color((int)(100 * t), (int)(50 * t), (int)(30 * t)));
        g.setPaint(sky);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        // Subtitles/Epilogue text
        if (epilogueTimer > 100) {
            float ta = Math.min(1f, (epilogueTimer - 100) / 60f);
            g.setFont(new Font("SansSerif", Font.BOLD, 32));
            String txt = "";
            g.setColor(new Color(255, 255, 255, (int)(255 * ta)));
            int tx = (SCREEN_W - g.getFontMetrics().stringWidth(txt)) / 2;
            g.drawString(txt, tx, (int)(SCREEN_H * 0.85f));
        }

        if (epilogueTimer > 250) {
            float ta = Math.min(1f, (epilogueTimer - 250) / 60f);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            String txt2 = "TO BE CONTINUED";
            g.setColor(new Color(255, 200, 100, (int)(255 * ta)));
            int tx2 = (SCREEN_W - g.getFontMetrics().stringWidth(txt2)) / 2;
            g.drawString(txt2, tx2, (int)(SCREEN_H * 0.5f));
        }

        // Close-up eyes fade in later for dramatic effect
        if (epilogueTimer > 400) {
            float et = Math.min(1f, (epilogueTimer - 400) / 120f);
            g.setColor(new Color(0, 0, 0, (int)(200 * et)));
            g.fillRect(0, 0, SCREEN_W, SCREEN_H);
            
            int eyeY = (int)(SCREEN_H * 0.30f);
            int eyeW = (int)(SCREEN_W * 0.15f); 
            int eyeH = (int)(eyeW * 0.32f);
            int eyeGap = (int)(SCREEN_W * 0.08f);
            
            g.setColor(new Color(20, 12, 8, (int)(255 * et)));
            g.fillOval(SCREEN_W / 2 - eyeGap - eyeW, eyeY, eyeW, eyeH);
            g.fillOval(SCREEN_W / 2 + eyeGap, eyeY, eyeW, eyeH);
            
            int irisSize = (int)(eyeH * 0.7f);
            int irisColor = (int)(160);
            g.setColor(new Color(irisColor + 40, irisColor / 2 + 30, 20, (int)(255 * et)));
            g.fillOval(SCREEN_W / 2 - eyeGap - eyeW + (eyeW - irisSize)/2, eyeY + (eyeH - irisSize)/2, irisSize, irisSize);
            g.fillOval(SCREEN_W / 2 + eyeGap + (eyeW - irisSize)/2, eyeY + (eyeH - irisSize)/2, irisSize, irisSize);
            
            int pupilSize = (int)(irisSize * 0.45f);
            g.setColor(new Color(5, 3, 2, (int)(255 * et)));
            g.fillOval(SCREEN_W / 2 - eyeGap - eyeW + (eyeW - pupilSize)/2, eyeY + (eyeH - pupilSize)/2, pupilSize, pupilSize);
            g.fillOval(SCREEN_W / 2 + eyeGap + (eyeW - pupilSize)/2, eyeY + (eyeH - pupilSize)/2, pupilSize, pupilSize);
        }

        if (epilogueTimer > 600) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.setColor(new Color(180, 170, 140, 200));
            String s = "Press any key to exit";
            g.drawString(s, (SCREEN_W - g.getFontMetrics().stringWidth(s)) / 2, SCREEN_H - 50);
        }
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE) {
            if (gameState.equals(STATE_TITLE)) System.exit(0);
            else if (gameState.equals(STATE_PLAYING)) System.exit(0);
            else if (gameState.equals(STATE_EPILOGUE)) {
                gameState = STATE_TITLE;
                return;
            }
        }
        switch (gameState) {
            case STATE_TITLE -> {
                if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
                    cutsceneSlide = 0; cutsceneTimer = 0;
                    gameState = STATE_CUTSCENE;
                }
            }
            case STATE_CUTSCENE -> {
                if (code == KeyEvent.VK_ENTER) advanceCutscene();
                if (code == KeyEvent.VK_ESCAPE) initWorld();
            }
            case STATE_DIALOGUE -> {
                if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) advanceDialogue();
            }
            case STATE_PLAYING -> {
                if (player != null) player.keyPressed(code);
            }
            case STATE_EPILOGUE -> {
                if (epilogueTimer > 360) gameState = STATE_TITLE;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Repassa keyReleased independente do estado para evitar teclas travadas
        if (player != null) {
            player.keyReleased(e.getKeyCode());
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        updateMouse(e);
        switch (gameState) {
            case STATE_TITLE    -> { cutsceneSlide = 0; cutsceneTimer = 0; gameState = STATE_CUTSCENE; }
            case STATE_CUTSCENE -> advanceCutscene();
            case STATE_DIALOGUE -> advanceDialogue();
            case STATE_PLAYING  -> {
                if (player != null) {
                    float wmx = mouseX / ZOOM + camX;
                    float wmy = mouseY / ZOOM + camY;
                    boolean fired = player.fireHook(wmx, wmy);
                    if (fired) audio.sfxHookShoot();
                }
            }
            case STATE_EPILOGUE -> { if (epilogueTimer > 360) gameState = STATE_TITLE; }
        }
    }

    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    private void updateMouse(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override public void mouseMoved(MouseEvent e)   { updateMouse(e); }
    @Override public void mouseDragged(MouseEvent e) { updateMouse(e); }

    // ─── Dialogue advance ─────────────────────────────────────────────────────

    private void advanceDialogue() {
        if (dialogue == null) return;
        boolean more = dialogue.advance();
        if (!more) {
            if (exitReached && "EXIT_REACHED".equals(pendingDialogue)) {
                gameState = STATE_EPILOGUE;
                epilogueTimer = 0;
            } else if ("INTRO".equals(pendingDialogue)) {
                pendingDialogue = "WAKEUP";
                dialogue.show("WAKEUP");
            } else {
                gameState = STATE_PLAYING;
                pendingDialogue = null;
            }
        }
    }
}
