package com.beanstalk;

import java.util.List;

/**
 * The hard-coded 20-page storyboard + the prompt rules.
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
     * @param text      exact story text drawn programmatically (null for page 20)
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
            "Beans! she cried. And out the window they went.", Zone.BOTTOM),

        new Page(4, "Moonlit Garden",
            JACK + ", kneeling in a moonlit cottage garden, gently picking up scattered beans from the soil "
          + "and cupping them in his hands, face lit softly by the moon.",
            LOWER,
            "That night, Jack crept into the garden and gathered every bean he could find.", Zone.BOTTOM),

        new Page(5, "Planting the Seeds",
            JACK + ", kneeling in the cottage garden in warm morning light, planting the magic seeds: "
          + "pressing a bean into a small hole in the soil with a little wooden trowel beside him, smiling "
          + "with hope. A few more beans rest in his other palm.",
            LOWER,
            "In the morning light, he dug little holes and planted the seeds, one by one.", Zone.BOTTOM),

        new Page(6, "Something Grows",
            JACK + ", fast asleep in a cosy attic bed. Through the small window behind him, a glowing green "
          + "sprout is curling up toward the stars, magical sparkles in the air.",
            "The top third of the image is a calm, dark, softly glowing night wall.",
            "Jack fell asleep. And while he dreamed, something began to grow.", Zone.TOP),

        new Page(7, "The Giant Stalk",
            JACK + ", standing at the foot of an enormous green beanstalk, seen from behind and the side, "
          + "head tilted back, gazing up at it as it towers through the clouds in the golden sunrise.",
            UPPER,
            "By sunrise, a beanstalk had burst through the clouds!", Zone.TOP),

        new Page(8, "Climbing the Beanstalk",
            JACK + ", climbing the giant beanstalk, gripping the thick vines with both hands, one foot "
          + "stepping up on a broad leaf, glancing toward the camera with determined excitement. The cottage "
          + "is small far below.",
            LOWER,
            "Jack took a deep breath and began to climb.", Zone.BOTTOM),

        new Page(9, "Above the Clouds",
            JACK + ", reaching the top of the beanstalk and peeking over a fluffy cloud floor, a vast "
          + "castle looming in the distance under a bright sky, face full of awe.",
            LOWER,
            "Up and up he went, until the world below grew tiny.", Zone.BOTTOM),

        new Page(10, "The Castle",
            JACK + ", standing small on a cloud path in front of a gigantic stone castle with an enormous "
          + "wooden door, seen from a low angle, looking up at it bravely.",
            UPPER,
            "At the top stood a castle as big as a mountain.", Zone.TOP),

        new Page(11, "Fee-Fi-Fo-Fum",
            "Inside a giant's kitchen with enormous furniture. " + JACK + ", peeks out from behind a huge "
          + "copper pot, eyes wide. A huge friendly-grumpy giant with a bushy beard strides in, sniffing the air.",
            LOWER,
            "Fee-fi-fo-fum! the giant roared. I smell a boy!", Zone.BOTTOM),

        new Page(12, "The Giant's Gold",
            "A giant sitting at an oversized table counting piles of shiny gold coins, then dozing off with "
          + "his chin on his chest. In the foreground, " + JACK + ", peeks around a table leg, watching.",
            UPPER,
            "The giant counted his gold... until his big eyes grew heavy.", Zone.TOP),

        new Page(13, "The Great Escape",
            JACK + ", sliding down the giant beanstalk, clutching a heavy sack of gold coins over his "
          + "shoulder, grinning with excitement, clouds streaming past.",
            LOWER,
            "Jack grabbed a bag of gold and slid down the beanstalk.", Zone.BOTTOM),

        new Page(14, "Mother's Joy",
            JACK + ", and his mother in the cottage garden at golden hour, she clasping her hands in joy "
          + "over an open sack of gold coins, both laughing and hugging.",
            LOWER,
            "Mother laughed and cried. They would never be hungry again.", Zone.BOTTOM),

        new Page(15, "The Golden Hen",
            JACK + ", tiptoeing in the giant's kitchen toward a plump hen sitting on a giant table, "
          + "a shining golden egg gleaming beside her, Jack reaching out gently.",
            UPPER,
            "Jack climbed again, and found a hen that laid golden eggs.", Zone.TOP),

        new Page(16, "The Magic Harp",
            JACK + ", looking up in wonder at a magical golden harp with a gentle glowing face that floats "
          + "on a high shelf in the giant's castle, music notes of light swirling around it.",
            LOWER,
            "Then he heard a harp, singing sweetly all by itself.", Zone.BOTTOM),

        new Page(17, "The Chase",
            JACK + ", scrambling down the beanstalk, hugging a small golden harp, while above him the angry "
          + "giant climbs down after him through the leaves, clouds swirling.",
            LOWER,
            "The giant woke with a roar and chased Jack down the stalk!", Zone.BOTTOM),

        new Page(18, "Timber!",
            JACK + ", at the base of the beanstalk, swinging a small axe into the thick trunk with all his "
          + "strength, wood chips flying, while the giant stalk begins to tilt above.",
            UPPER,
            "Jack grabbed an axe and chopped. Down came the beanstalk!", Zone.TOP),

        new Page(19, "Happily Ever After",
            JACK + ", his mother, the golden hen and the little golden harp together in front of their "
          + "cosy cottage in a sunny garden full of flowers, everyone smiling, warm sunset light.",
            LOWER,
            "And Jack and his mother lived happily ever after. The End.", Zone.BOTTOM)
    );

    /** Page 20: portrait of the child (same reference, same style string). */
    public static final Page ABOUT_ME_PORTRAIT = new Page(20, "About Me",
        "A friendly head-and-shoulders portrait of " + JACK + ", smiling warmly at the camera, front-lit, "
      + "centred, against a soft sunny meadow background with a tiny beanstalk far behind.",
        "The lower 45% of the image is a calm, softly blurred, empty meadow.",
        null, Zone.BOTTOM);
}
