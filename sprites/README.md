# YARIPO — Sprites Folder

Place PNG sprite files here to replace the geometric placeholders used in the game.

## Sprite Files and Keys

| Filename             | Key            | Description                          | Recommended Size |
|----------------------|----------------|--------------------------------------|------------------|
| player_right.png     | player_right   | Player facing right (idle/walk)      | 16x24 px         |
| player_left.png      | player_left    | Player facing left (idle/walk)       | 16x24 px         |
| tile_solid.png       | tile_solid     | Standard solid wall/floor tile       | 20x20 px         |
| tile_hook.png        | tile_hook      | Hookable surface tile                | 20x20 px         |
| tile_water.png       | tile_water     | Water tile (lake bottom)             | 20x20 px         |
| tile_acid.png        | tile_acid      | Acid tile (hazard)                   | 20x20 px         |
| item_hook.png        | item_hook      | Hook item pickup sprite              | 16x16 px         |
| item_pickaxe.png     | item_pickaxe   | Pickaxe item pickup sprite           | 16x16 px         |
| item_boots.png       | item_boots     | Boots item pickup sprite             | 16x16 px         |
| hook_head.png        | hook_head      | The tip of the fired hook            | 8x8 px           |

## How to Swap Sprites

1. Create or obtain PNG files matching the names above.
2. Copy them into this `sprites/` folder next to the game JAR (or in the working directory from which you run the game).
3. Run the game — sprites are loaded at startup. If a file is not found, the game falls back to colored geometric shapes so it always runs.
4. Sprites do NOT need to match the recommended sizes exactly; they will be scaled to fit the tile or character bounding box automatically.

## Coordinate System

- Tiles are 20x20 pixels in world space.
- The player bounding box is 16x24 pixels centered on the player position.
- The camera follows the player with smooth interpolation (camX, camY in Game.java).

## Tips

- Keep PNGs small (< 64x64) for best performance — they are drawn every frame.
- Use transparency (alpha channel) for non-rectangular shapes.
- Animated sprites are not yet supported; replace the file with the frame you want displayed.
