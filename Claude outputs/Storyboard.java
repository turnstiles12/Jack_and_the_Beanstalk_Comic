package com.beanstalk;

import java.util.List;

/**
 * The hard-coded 3-page storyboard (The Empty Cupboard, The Trade, Out the Window) + the prompt rules.
 *
 * Rules enforced here:
 *  - STYLE is one immutable constant, appended verbatim as the LAST sentence of every prompt.
 *  - Prompts never ask the model to draw text/letters/signs/books (those scene elements are avoided).
 *  - Jack's outfit is one constant reused on every page.
 *  - Every page declares which zone is "quiet" so the PDF composer knows where to put text.
 */
public final class Storyboard {

    private Storyboard() {}

    /** Exactly as specified. Never modify, never append to. */
    public static final String STYLE =
            "Modern 3D animated feature — rounded forms, large expressive eyes, soft rim light.";

    /** Same clothes on every page. */
    public static final String JACK =
            "Jack, a young child {LOOKS}, wearing a rust-orange tunic, "
          + "a brown leather vest, tan trousers, and a small green cap";

    /** Fills {LOOKS} for providers that receive the photo (openai, fal, mock). */
    public static final String PHOTO_LOOKS = "with the face from the reference photo";

    /** Sent as negative_prompt only (never in the positive prompt, which would plant the idea of text). */
    public static final String NEGATIVE =
            "text, letters, words, captions, writing, signature, watermark, logo, speech bubble, "
          + "deformed face, different face, extra fingers, blurry";

    public enum Zone { TOP, BOTTOM }

    /**
     * @param number    1-based page number
     * @param title     internal title
     * @param scene     scene description (no text-bearing objects)
     * @param quiet     sentence telling the model which area to keep calm/empty (for the text overlay)
     * @param text      exact story text drawn programmatically 
     * @param zone      where the text panel is drawn
     */
    public record Page(int number, String title, String scene, String quiet, String text, Zone zone) {

        /** Final positive prompt: scene + quiet-zone note + the fixed style string, last. */
        public String prompt() {
            return prompt(PHOTO_LOOKS);
        }

        /** Same, with the child's look filled in ("with short curly brown hair, brown eyes" for text-only models). */
        public String prompt(String looks) {
            return (scene + " " + quiet).replace("{LOOKS}", looks) + " " + STYLE;
        }
    }

    private static final String LOWER = "The lower third of the image is calm, uncluttered and softly lit.";
    private static final String UPPER = "The top third of the image is calm, open and uncluttered.";

    public static final List<Page> PAGES = List.of(
        new Page(1, "The Empty Cupboard",
            JACK + ", standing in a bare, rustic cottage kitchen with empty shelves, looking up at his mother, "
          + "full face, front-lit, facing the camera three-quarters toward her. His kind mother in a patched "
          + "blue dress stands beside him. Through the open door behind them, the white-and-brown cow "
          + "Milky-White is visible in the yard.",
            LOWER,
            "Jack and his mother had nothing left to sell but Milky-White.", Zone.BOTTOM),

        new Page(2, "The Trade",
            "A dirt road to market winding through green hills. A kindly old man in a patched cloak holds out "
          + "five brightly coloured beans in his open palm. " + JACK + ", seen in three-quarter view, holds "
          + "the rope of a white-and-brown cow, looking at the beans with wonder.",
            "The sky at the top of the image is wide, open and calm.",
            "Five magic beans, said the old man. For one cow.", Zone.TOP),

        new Page(3, "Out the Window",
            "The cottage at dusk under a deep indigo sky, a darker palette than the earlier scenes. "
          + "A mother leans out of an upper window and throws handfuls of beans that scatter into the garden "
          + "below. " + JACK + ", stands at the cottage doorway, watching with wide eyes.",
            "The foreground dark grass in the lower third is quiet, dark and empty.",
            "Beans! she cried. And out the window they went.", Zone.BOTTOM)
    );

}
