package yaripo;

import java.awt.Color;
import java.util.*;

/**
 * Bilingual dialogue system. Each line stores PT and EN.
 */
public class Dialogue {

    public record DlgLine(String speakerEN, String textEN, Color color) {}

    public static final Color COLOR_NARRATOR  = new Color(220, 220, 180);
    public static final Color COLOR_PLAYER    = new Color(160, 220, 255);
    public static final Color COLOR_CAVE      = new Color(255, 215,  60);
    public static final Color COLOR_IARA      = new Color( 80, 180, 255);
    public static final Color COLOR_VISION    = new Color(200, 140, 255);
    public static final Color COLOR_REVANCHE  = new Color(255, 100, 100);

    private static final Map<String, List<DlgLine>> ALL = new LinkedHashMap<>();

    static {
        ALL.put("INTRO", List.of(
            new DlgLine("Narrator",
                "The Black River suffers a devastating drought. The village believes it is the wrath of the gods.",
                COLOR_NARRATOR),
            new DlgLine("Narrator",
                "In truth, her father — the Shaman — accepted bribes from miners to silence the pollution.",
                COLOR_NARRATOR),
            new DlgLine("Narrator",
                "To divert attention, he invented that the gods demanded a human sacrifice.",
                COLOR_NARRATOR),
            new DlgLine("Narrator",
                "But nature itself reacted. A mystical lightning struck the leader's house.",
                COLOR_NARRATOR),
            new DlgLine("Narrator",
                "The village read it as an irrefutable command: the Shaman's daughter must be the offering.",
                COLOR_NARRATOR),
            new DlgLine("Narrator",
                "You were cast into the abyss of Misty Peak. Your fall ends now.",
                COLOR_NARRATOR)
        ));

        ALL.put("WAKEUP", List.of(
            new DlgLine("Aruana",
                "I... am still alive? The waters of the mystical lake healed my body.",
                COLOR_PLAYER),
            new DlgLine("The Cave",
                "WAKE UP, DAUGHTER OF THE EARTH. USE WHAT THE MEN OF METAL LEFT BEHIND.",
                COLOR_CAVE),
            new DlgLine("The Cave",
                "THE TRUTH IS BURIED BENEATH GOLD AND BLOOD. CLIMB.",
                COLOR_CAVE)
        ));

        ALL.put("HOOK_PICKUP", List.of(
            new DlgLine("Aruana",
                "A hook with a steel cable... left by the miners. I can climb with this.",
                COLOR_PLAYER),
            new DlgLine("Narrator",
                "[HOOK acquired] Use the MOUSE to aim and click on light stone ceilings. Space to release.",
                COLOR_NARRATOR)
        ));

        ALL.put("PICK_PICKUP", List.of(
            new DlgLine("Aruana",
                "A heavy pickaxe. This will serve to open a path through cracked rocks.",
                COLOR_PLAYER),
            new DlgLine("Narrator",
                "[PICKAXE acquired] Approach cracked walls and press E.",
                COLOR_NARRATOR)
        ));

        ALL.put("BOOT_PICKUP", List.of(
            new DlgLine("Aruana",
                "Miner boots. They are resistant... the river's poison won't burn my feet.",
                COLOR_PLAYER),
            new DlgLine("Narrator",
                "[BOOTS acquired] You are now immune to acid mud and chemical waste.",
                COLOR_NARRATOR)
        ));

        ALL.put("IARA_VOICE", List.of(
            new DlgLine("Sweet Voice",
                "Psst... come here, little one. I know a safe shortcut.",
                COLOR_IARA),
            new DlgLine("Sweet Voice",
                "Your father is repentant. He sent me to find you.",
                COLOR_IARA),
            new DlgLine("Aruana",
                "My father? He... he cares?",
                COLOR_PLAYER),
            new DlgLine("Sweet Voice",
                "Very much. Follow my song to the right. Don't look at the shadows.",
                COLOR_IARA)
        ));

        ALL.put("CAVE_VOICE", List.of(
            new DlgLine("The Cave",
                "DO NOT LISTEN TO THE WATER SERPENT. HER SONG IS MADE OF LIES AND MUD.",
                COLOR_CAVE),
            new DlgLine("The Cave",
                "GOLD BLINDED THE SHAMAN. HE CHOSE METAL OVER HIS OWN BLOOD.",
                COLOR_CAVE)
        ));

        ALL.put("VISION", List.of(
            new DlgLine("Vision of the Past",
                "The stones show your father accepting bribes while the Black River died.",
                COLOR_VISION),
            new DlgLine("Vision of the Past",
                "The sacrifice was not for the gods. It was to silence your voice.",
                COLOR_VISION),
            new DlgLine("Aruana",
                "My death was the price of his silence. But the earth did not stay quiet.",
                COLOR_PLAYER)
        ));

        ALL.put("FATHER_GUILT", List.of(
            new DlgLine("Aruana",
                "My father... how could he? Was money more important than his own daughter?",
                COLOR_PLAYER),
            new DlgLine("The Cave",
                "THE SHAMAN FORGOT HIS ROOTS. HE ONLY SEES THE GOLDEN SURFACE.",
                COLOR_CAVE)
        ));

        ALL.put("MINER_CAMP", List.of(
            new DlgLine("Aruana",
                "An abandoned camp. They used these machines to tear the heart of the mountain.",
                COLOR_PLAYER),
            new DlgLine("Narrator",
                "Remains of illegal mining are everywhere. Nature is taking back the metal.",
                COLOR_NARRATOR)
        ));

        ALL.put("RIVER_TEARS", List.of(
            new DlgLine("Aruana",
                "I can hear the river crying. The poison they poured still burns.",
                COLOR_PLAYER),
            new DlgLine("The Cave",
                "THE BLACK RIVER WILL ONLY RUN CLEAN AGAIN WHEN THE TRUTH IS REVEALED.",
                COLOR_CAVE)
        ));

        ALL.put("EXIT_REACHED", List.of(
            new DlgLine("Narrator",
                "Daylight floods the cave. You are at the top of Misty Peak.",
                COLOR_NARRATOR),
            new DlgLine("Aruana",
                "I see the village down there. The Black River still cries poison.",
                COLOR_PLAYER),
            new DlgLine("Aruana",
                "The journey has not ended. It has only changed direction.",
                COLOR_PLAYER),
            new DlgLine("Narrator",
                "Armed with the truth and the tools of your enemies, you begin the descent.",
                COLOR_NARRATOR),
            new DlgLine("Aruana",
                "Father... I am coming back.",
                COLOR_REVANCHE)
        ));
    }

    // ── Runtime state ────────────────────────────────────────────────────────

    private List<DlgLine> currentSequence = List.of();
    private int     currentIndex = 0;
    private boolean active = false;

    public void show(String key) {
        List<DlgLine> seq = ALL.get(key);
        if (seq == null || seq.isEmpty()) return;
        currentSequence = seq;
        currentIndex = 0;
        active = true;
    }

    public boolean advance() {
        if (!active) return false;
        currentIndex++;
        if (currentIndex >= currentSequence.size()) {
            active = false;
            return false;
        }
        return true;
    }

    public boolean isActive() { return active; }
    public DlgLine current()  { return active ? currentSequence.get(currentIndex) : null; }
}
