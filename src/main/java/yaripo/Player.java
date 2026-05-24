package yaripo;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Player physics, state, tool logic.
 * Hook state machine: IDLE -> FIRED -> ATTACHED -> IDLE
 */
public class Player {

    // ── Constants ────────────────────────────────────────────────────────────
    public static final float GRAVITY    = 0.55f;
    public static final float JUMP_FORCE = -12.5f;
    public static final float MOVE_SPEED = 4.8f;
    public static final float MAX_FALL   = 16f;
    public static final float HOOK_SPEED     = 30f;
    public static final float MAX_HOOK_DIST  = 400f;
    public static final int   ACID_DEATH_FRAMES = 120;
    public static final float PICKUP_RADIUS_TILES = 3.5f;

    // ── Bounding box (x = center, y = bottom) ────────────────────────────────
    public static final int W = 30, H = 42;

    public float x, y;
    public float vx, vy;

    public boolean onGround;
    public boolean facingRight = true;
    public boolean inWater;
    public boolean inAcid;
    private int    acidTimer;

    // Stats
    public float hp        = 1f;   // 0..1
    public float stamina   = 1f;   // 0..1
    private static final float STAMINA_DRAIN  = 0.001f;
    private static final float STAMINA_REGEN  = 0.02f;
    private static final float ACID_HP_DRAIN  = 0.002f;

    // Inventory
    public boolean hasHook;
    public boolean hasPickaxe;
    public boolean hasBoots;

    // Hook state machine
    public enum HookState { IDLE, FIRED, PULLING }
    public HookState hookState = HookState.IDLE;

    public float hookX, hookY;
    private float hookVX, hookVY;
    private float hookTravelled;
    public float hookAnchorX, hookAnchorY;

    // Pull-to-point speed
    private static final float PULL_SPEED = 12f;

    // Hook hint timer
    public int hookHintTimer;

    public String state = "idle";  // "idle","walk","jump","swing","dead","acid"
    public boolean dead = false;

    private boolean keyLeft, keyRight, keyUp, keyJumpHeld;
    public  boolean keyPickaxe;
    public  int     walkAnimTick;

    // Pickup event ('1','2','3', or 0)
    public char justPickedUp;

    public Player(float startX, float startY) {
        this.x = startX;
        this.y = startY;
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    public void releaseKeys() {
        keyLeft = false;
        keyRight = false;
        keyUp = false;
        keyJumpHeld = false;
        keyPickaxe = false;
    }

    public void keyPressed(int code) {
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> keyLeft = true;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> keyRight = true;
            case KeyEvent.VK_UP, KeyEvent.VK_W, KeyEvent.VK_SPACE -> { keyUp = true; keyJumpHeld = true; tryJump(); }
            case KeyEvent.VK_E -> keyPickaxe = true;
            case KeyEvent.VK_R, KeyEvent.VK_F -> releaseHook();
        }
    }

    public void keyReleased(int code) {
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> keyLeft = false;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> keyRight = false;
            case KeyEvent.VK_UP, KeyEvent.VK_W, KeyEvent.VK_SPACE -> { keyUp = false; keyJumpHeld = false; }
            case KeyEvent.VK_E -> keyPickaxe = false;
        }
    }

    private void tryJump() {
        if (hookState == HookState.PULLING) {
            releaseHook();
            return;
        }
        if (onGround && !dead) {
            vy = JUMP_FORCE;
            onGround = false;
        } else if (inWater && !dead) {
            vy = JUMP_FORCE * 0.7f;
        }
    }

    // ─── Hook ─────────────────────────────────────────────────────────────────

    public boolean fireHook(float tx, float ty) {
        if (!hasHook || dead) return false;
        if (hookState != HookState.IDLE) {
            releaseHook();
            return false;
        }
        float dx = tx - x, dy = ty - (y - H / 2f);
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 5f) return false;
        hookVX = dx / dist * HOOK_SPEED;
        hookVY = dy / dist * HOOK_SPEED;
        hookX  = x;
        hookY  = y - H / 2f;
        hookTravelled = 0;
        hookState = HookState.FIRED;
        return true;
    }

    public void releaseHook() {
        hookState = HookState.IDLE;
        hookHintTimer = 0;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(TileMap map) {
        justPickedUp = 0;
        if (dead) return;

        if (hookHintTimer > 0) hookHintTimer--;

        if (hookState == HookState.FIRED) {
            updateHookProjectile(map);
        }

        if (hookState == HookState.PULLING) {
            updatePull(map);
        }

        // Stamina: drains while pulling hook, regens on ground
        if (hookState == HookState.PULLING) {
            stamina = Math.max(0f, stamina - STAMINA_DRAIN);
        } else if (onGround) {
            stamina = Math.min(1f, stamina + STAMINA_REGEN);
        }

        if (hookState != HookState.PULLING) {
            // Normal locomotion
            if (keyLeft)  { vx = -MOVE_SPEED; facingRight = false; }
            if (keyRight) { vx =  MOVE_SPEED; facingRight = true;  }
            if (!keyLeft && !keyRight) vx *= 0.80f;

            if (inWater) {
                vy += GRAVITY * 0.3f;
                vy *= 0.85f;
                vx *= 0.90f;
            } else {
                vy += GRAVITY;
            }
            vy = Math.min(vy, MAX_FALL);

            resolveX(map);
            resolveY(map);
            unstick(map);
        }

        checkEnvironment(map);

        // Item pickup
        char item = map.tryPickup(x, y - H / 2f, PICKUP_RADIUS_TILES * TileMap.TILE);
        if (item != 0) {
            justPickedUp = item;
            if (item == '1') hasHook = true;
            if (item == '2') hasPickaxe = true;
            if (item == '3') hasBoots = true;
        }

        // Acid damage
        if (inAcid) {
            if (!hasBoots) {
                acidTimer++;
                hp = Math.max(0f, hp - ACID_HP_DRAIN);
                if (hp <= 0f || acidTimer >= ACID_DEATH_FRAMES) die();
            }
        } else {
            acidTimer = Math.max(0, acidTimer - 2);
            if (!inAcid) hp = Math.min(1f, hp + 0.002f); // slow regen
        }

        if (Math.abs(vx) > 0.1f) walkAnimTick++;

        if (dead) state = "dead";
        else if (inAcid && !hasBoots) state = "acid";
        else if (hookState == HookState.PULLING) state = "jump";
        else if (!onGround) state = "jump";
        else if (Math.abs(vx) > 0.5f) state = "walk";
        else state = "idle";
    }

    private void updateHookProjectile(TileMap map) {
        final int SUBSTEPS = 10;
        float sx = hookVX / SUBSTEPS;
        float sy = hookVY / SUBSTEPS;
        for (int s = 0; s < SUBSTEPS; s++) {
            hookX += sx;
            hookY += sy;
            hookTravelled += (float) Math.hypot(sx, sy);

            if (hookTravelled > MAX_HOOK_DIST) { hookState = HookState.IDLE; return; }
            if (hookX < 0 || hookX > TileMap.COLS * TileMap.TILE ||
                hookY < 0 || hookY > TileMap.ROWS * TileMap.TILE) {
                hookState = HookState.IDLE; return;
            }
            int col = (int)(hookX / TileMap.TILE);
            int row = (int)(hookY / TileMap.TILE);
            if (map.isHookable(col, row)) {
                hookAnchorX = col * TileMap.TILE + TileMap.TILE / 2f;
                hookAnchorY = row * TileMap.TILE + TileMap.TILE / 2f;
                hookX = hookAnchorX;
                hookY = hookAnchorY;
                hookState = HookState.PULLING;
                hookHintTimer = 120;
                return;
            }
            if (map.isSolid(col, row)) {
                hookState = HookState.IDLE;
                return;
            }
        }
    }

    /** Pulls player toward anchor. If there is room above the hookable tile, lands on top; otherwise hangs below. */
    private void updatePull(TileMap map) {
        int col = (int)(hookAnchorX / TileMap.TILE);
        int row = (int)(hookAnchorY / TileMap.TILE);

        float topY   = row * TileMap.TILE - 0.01f;          // bottom of player standing on top
        float hangY  = row * TileMap.TILE + TileMap.TILE + H + 0.01f; // bottom of player hanging below

        float cx = col * TileMap.TILE + TileMap.TILE / 2f;

        // Check if there is room for the player above the hookable tile
        boolean roomAbove = !map.rectSolid(cx - W / 2f, topY - H, W, H, hasBoots);

        if (roomAbove) {
            x = cx;
            y = topY;
            vx = 0; vy = 0;
            onGround = true;
        } else {
            // Hang below the tile: give a small upward boost so player swings/falls naturally
            x = cx;
            y = hangY;
            vx = 0; vy = -2f;
            onGround = false;
        }
        releaseHook();
        unstick(map);
    }

    // ─── Collision resolution with substeps ─────────────────────────────────

    private void resolveX(TileMap map) {
        float remaining = vx;
        float step = TileMap.TILE / 2f - 1f;
        while (Math.abs(remaining) > 0.001f) {
            float move = Math.max(-step, Math.min(step, remaining));
            float nx = x + move;
            if (map.rectSolid(nx - W / 2f, y - H, W, H, hasBoots)) {
                // Snap against wall
                if (move > 0) {
                    int col = (int)((nx + W / 2f - 1) / TileMap.TILE);
                    x = col * TileMap.TILE - W / 2f - 0.01f;
                } else {
                    int col = (int)((nx - W / 2f) / TileMap.TILE);
                    x = (col + 1) * TileMap.TILE + W / 2f + 0.01f;
                }
                vx = 0;
                return;
            }
            x = nx;
            remaining -= move;
        }
    }

    private void resolveY(TileMap map) {
        float remaining = vy;
        float step = TileMap.TILE / 2f - 1f;
        onGround = false;
        while (Math.abs(remaining) > 0.001f) {
            float move = Math.max(-step, Math.min(step, remaining));
            float ny = y + move;
            if (map.rectSolid(x - W / 2f, ny - H, W, H, hasBoots)) {
                if (move > 0) {
                    int row = (int)((ny - 1) / TileMap.TILE);
                    y = row * TileMap.TILE - 0.01f;
                    onGround = true;
                } else {
                    int row = (int)((ny - H) / TileMap.TILE);
                    y = (row + 1) * TileMap.TILE + H + 0.01f;
                }
                vy = 0;
                return;
            }
            y = ny;
            remaining -= move;
        }
        // Ground check
        if (map.rectSolid(x - W / 2f, y + 0.5f, W, 1, hasBoots)) onGround = true;
    }

    private void unstick(TileMap map) {
        if (!map.rectSolid(x - W / 2f, y - H, W, H, hasBoots)) return;
        // Push out: try up first, then sides
        for (int dy = 1; dy <= TileMap.TILE; dy++) {
            if (!map.rectSolid(x - W / 2f, y - H - dy, W, H, hasBoots)) { y -= dy; return; }
        }
        for (int dx = 1; dx <= TileMap.TILE; dx++) {
            if (!map.rectSolid(x - W / 2f + dx, y - H, W, H, hasBoots)) { x += dx; return; }
            if (!map.rectSolid(x - W / 2f - dx, y - H, W, H, hasBoots)) { x -= dx; return; }
        }
    }

    private void checkEnvironment(TileMap map) {
        int col = (int)(x / TileMap.TILE);
        int rowTop = (int)((y - H + 2) / TileMap.TILE);
        int rowMid = (int)((y - H / 2f) / TileMap.TILE);
        int rowBot = (int)((y - 1) / TileMap.TILE);
        inWater = map.isWater(col, rowMid) || map.isWater(col, rowTop);
        inAcid  = map.isAcid(col, rowBot) || map.isAcid(col, rowMid);
    }

    // ─── Pickaxe ──────────────────────────────────────────────────────────────

    public boolean tryBreak(TileMap map) {
        if (!hasPickaxe) return false;
        int col = (int)(x / TileMap.TILE);
        int row = (int)((y - H / 2f) / TileMap.TILE);
        int[] dcols = facingRight ? new int[]{1, 1, 0, -1, 1} : new int[]{-1, -1, 0, 1, -1};
        int[] drows = {0, -1, -1, 0, 1};
        for (int i = 0; i < dcols.length; i++) {
            if (map.breakTile(col + dcols[i], row + drows[i])) return true;
        }
        return false;
    }

    // ─── Death / respawn ──────────────────────────────────────────────────────

    public void die() {
        if (dead) return;
        dead = true;
        state = "dead";
        hookState = HookState.IDLE;
        vx = 0;
        vy = -4f;
    }

    public void respawn(float sx, float sy) {
        x = sx; y = sy;
        vx = 0; vy = 0;
        dead = false; inAcid = false; inWater = false;
        acidTimer = 0;
        hp = 1f;
        stamina = 1f;
        hookState = HookState.IDLE;
        state = "idle";
        onGround = false;
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g, float camX, float camY, Sprites sprites, long tick) {
        float sx = x - camX;
        float sy = y - camY;

        // Rope
        if (hookState == HookState.FIRED || hookState == HookState.PULLING) {
            float hsx = hookX - camX, hsy = hookY - camY;
            g.setColor(new Color(180, 140, 80));
            g.setStroke(new BasicStroke(hookState == HookState.PULLING ? 2f : 1.5f));
            g.drawLine((int)(sx), (int)(sy - H / 2), (int) hsx, (int) hsy);
            g.setStroke(new BasicStroke(1f));
            sprites.drawHookHead(g, hookX - camX, hookY - camY, hookState == HookState.FIRED);
        }

        if (inAcid && !hasBoots && acidTimer > 0) {
            float flash = (acidTimer % 10 < 5) ? 0.6f : 0f;
            g.setColor(new Color(1f, 0.2f, 0f, flash));
            g.fillOval((int)(sx - W / 2 - 4), (int)(sy - H - 4), W + 8, H + 8);
        }

        sprites.drawPlayer(g, sx, sy, facingRight, state, walkAnimTick);

        if (inAcid && !hasBoots && acidTimer > 0) {
            float pct = 1f - (float) acidTimer / ACID_DEATH_FRAMES;
            g.setColor(new Color(60, 200, 40, 160));
            g.fillRect((int)(sx - W / 2), (int)(sy - H - 6), (int)(W * pct), 3);
            g.setColor(new Color(220, 40, 40, 200));
            g.fillRect((int)(sx - W / 2 + (int)(W * pct)), (int)(sy - H - 6),
                       W - (int)(W * pct), 3);
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public boolean isHookAvailable() { return hasHook && hookState == HookState.IDLE; }
    public boolean isHookActive()    { return hookState != HookState.IDLE; }
    public float   getAcidPct()      { return (float) acidTimer / ACID_DEATH_FRAMES; }
    public int     getTileCol()      { return (int)(x / TileMap.TILE); }
    public int     getTileRow()      { return (int)((y - H / 2f) / TileMap.TILE); }
}
