package com.beanstalk;

import java.util.List;

import static com.beanstalk.Storyboard.LOWER;
import static com.beanstalk.Storyboard.UPPER;
import static com.beanstalk.Storyboard.Zone.BOTTOM;
import static com.beanstalk.Storyboard.Zone.TOP;

/**
 * The built-in Little Red Riding Hood template: same 20-page layout as Jack and the Beanstalk
 * (identical page numbers and text zones), different scenes and story text.
 *
 * Rules, same as {@link Storyboard}: every prompt ends with the fixed STYLE sentence, no prompt asks the model to
 * draw lettering, the hero's outfit is one constant, and the story text is drawn later by the PDF composer.
 * Characters are placed away from the zone that holds the text, so the text panel does not cover a face.
 * The story text avoids pronouns for Red so any photo fits.
 */
public final class RedRidingHood {

    private RedRidingHood() {}

    /** Same clothes on every page. */
    public static final String RED =
            "Red, a hero {LOOKS}, wearing a bright red hooded cloak over a cream blouse, "
          + "a mossy-green vest, brown trousers and sturdy brown boots";

    private static final String MOTHER = "a kind woman with a warm smile in a green apron";
    private static final String GRANDMA = "a sweet grandmother with silver hair in a bun, round spectacles and a lilac shawl";
    private static final String WOLF = "a tall grey wolf with a long muzzle, amber eyes and a cheeky grin";
    private static final String WOODCUTTER = "a burly woodcutter with a bushy brown beard, a red-checked shirt and a felling axe";

    /** Characters sit in the upper two thirds when the text panel is at the bottom, and vice versa. */
    private static final String HIGH = " All characters are in the upper two thirds of the frame.";
    private static final String LOW = " All characters are in the lower two thirds of the frame.";

    public static final List<Storyboard.Page> PAGES = List.of(
        new Storyboard.Page(1, "The Basket",
            RED + ", standing at the door of a cosy woodland cottage, holding a wicker basket covered with a "
          + "checked cloth. Beside them stands " + MOTHER + ". Full face, front-lit, facing the "
          + "camera three-quarters." + HIGH,
            LOWER,
            "Red's mother packed a basket for Grandmother, who was feeling poorly.", BOTTOM),

        new Storyboard.Page(2, "Stay on the Path",
            "A sunny cottage garden gate. " + MOTHER + ", gently holds up one finger in a warning, standing close to "
          + RED + ", who nods with the basket on one arm, a winding forest path behind them." + LOW,
            UPPER,
            "Stay on the path, Red's mother said, and Red promised to.", TOP),

        new Storyboard.Page(3, "Into the Forest",
            RED + ", skipping along a sun-dappled forest path between tall trees, basket swinging, "
          + "humming happily, seen in three-quarter view." + HIGH,
            LOWER,
            "Red skipped into the whispering forest, humming a happy tune.", BOTTOM),

        new Storyboard.Page(4, "The Wolf",
            RED + ", stopping on the forest path, surprised. " + WOLF + " steps out from behind a mossy old oak "
          + "tree, one paw raised in a friendly-looking wave." + HIGH,
            LOWER,
            "Halfway there, a wolf stepped out from behind an old oak.", BOTTOM),

        new Storyboard.Page(5, "Where Are You Going?",
            "In a forest clearing, " + WOLF + " leans down toward " + RED + ", who points along the path "
          + "with a trusting smile, basket in the other hand." + HIGH,
            LOWER,
            "Where are you going, friend? the wolf asked, ever so politely.", BOTTOM),

        new Storyboard.Page(6, "A Meadow of Flowers",
            RED + ", standing at the edge of a bright meadow full of red poppies and yellow daisies, "
          + "looking at the flowers with delight, while " + WOLF + " gestures toward the meadow with a playful smile." + LOW,
            UPPER,
            "Why not pick Grandmother some flowers? the wolf suggested with a toothy smile.", TOP),

        new Storyboard.Page(7, "Picking Flowers",
            RED + ", kneeling in the poppy meadow, happily gathering a bouquet of red flowers, basket "
          + "on the grass. Far behind in the trees, a grey wolf shape trots away along the path." + LOW,
            UPPER,
            "While Red picked flowers, the wolf raced ahead through the trees.", TOP),

        new Storyboard.Page(8, "Knock Knock",
            "A small stone cottage deep in the woods with a round wooden door and flower boxes. "
          + WOLF + " stands at the door with one paw raised to knock, glancing about playfully." + HIGH,
            LOWER,
            "He knocked at Grandmother's door. Who is it? she called.", BOTTOM),

        new Storyboard.Page(9, "Grandmother Hides",
            "Inside a cosy cottage bedroom, " + GRANDMA + " hurries toward a big wooden wardrobe, "
          + "peeking back over her shoulder with wide eyes. A grey wolf shadow stands at the door." + HIGH,
            LOWER,
            "Grandmother hid in the wardrobe as the wolf crept inside.", BOTTOM),

        new Storyboard.Page(10, "The Nightcap",
            "In a cosy cottage bedroom, " + WOLF + " tucked up in a bed wearing a frilly white nightcap and a "
          + "lilac shawl, the covers pulled up to his chin, a sly grin on his face." + LOW,
            UPPER,
            "The wolf put on her nightcap and snuggled into her bed.", TOP),

        new Storyboard.Page(11, "Red Arrives",
            RED + ", standing in the open doorway of Grandmother's cottage, holding the wicker basket in one "
          + "hand and a bouquet of red poppies in the other, looking curiously into the room." + HIGH,
            LOWER,
            "Red arrived with the basket and a bunch of flowers.", BOTTOM),

        new Storyboard.Page(12, "Big Ears",
            RED + ", standing beside the bed, leaning in and looking puzzled. In the bed, " + WOLF
          + " wears a frilly nightcap, with very big pointed ears poking through it." + LOW,
            UPPER,
            "Grandmother, what big ears you have! All the better to hear you with.", TOP),

        new Storyboard.Page(13, "Big Eyes",
            "A close view of " + WOLF + " in a frilly nightcap, eyes wide and glowing, while " + RED
          + " takes a small step back, eyes wide with worry." + HIGH,
            LOWER,
            "What big eyes you have! All the better to see you with.", BOTTOM),

        new Storyboard.Page(14, "Big Teeth",
            RED + " stepping back with a startled face as " + WOLF + " sits up in bed, nightcap crooked, "
          + "showing a very wide cheeky grin." + HIGH,
            LOWER,
            "And what big teeth you have! All the better to gobble you up!", BOTTOM),

        new Storyboard.Page(15, "The Chase",
            RED + ", running around a cottage table with the basket, hood flying, while " + WOLF
          + " leaps out of the bed after them, the nightcap tumbling off." + LOW,
            UPPER,
            "Red shrieked and ran around the room as the wolf leapt out of bed.", TOP),

        new Storyboard.Page(16, "The Woodcutter",
            WOODCUTTER + " bursts through the cottage door, axe held high, looking determined and kind, "
          + "while " + RED + " ducks behind a chair." + HIGH,
            LOWER,
            "Just then, a brave woodcutter burst through the door!", BOTTOM),

        new Storyboard.Page(17, "The Wolf Flees",
            "Outside the cottage, " + WOLF + " runs away into the forest with his tail between his legs, "
          + "while " + WOODCUTTER + " waves his axe. " + RED + " watches from the doorway." + HIGH,
            LOWER,
            "The wolf fled into the forest and was never seen again.", BOTTOM),

        new Storyboard.Page(18, "Safe and Sound",
            GRANDMA + " steps out of the wooden wardrobe smiling, and hugs " + RED
          + " tightly, while " + WOODCUTTER + " smiles beside them." + LOW,
            UPPER,
            "Grandmother climbed out of the wardrobe, safe and sound, and hugged Red tight.", TOP),

        new Storyboard.Page(19, "Tea Time",
            "Around a sunny cottage table, " + RED + ", " + GRANDMA + " and " + WOODCUTTER
          + " happily share cakes and treats from the wicker basket, a bouquet of red poppies in a jug, "
          + "warm golden light through the window." + HIGH,
            LOWER,
            "They shared the basket of treats, and Red never left the path again. The End.", BOTTOM)
    );

    /** Page 20: portrait of the hero (same reference, same style string). */
    public static final Storyboard.Page ABOUT_ME_PORTRAIT = new Storyboard.Page(20, "About Me",
        "A friendly head-and-shoulders portrait of " + RED + ", smiling warmly at the camera, front-lit, "
      + "centred, against a soft sunny forest clearing with red poppies and a tiny cottage far behind.",
        "The lower 45% of the image is a calm, softly blurred, empty forest floor.",
        null, BOTTOM);
}
