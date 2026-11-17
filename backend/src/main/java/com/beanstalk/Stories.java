package com.beanstalk;

import java.util.List;

/**
 * The built-in story templates. These are only the seed: {@link StoryTemplateStore} writes them to the database
 * and books are generated from the stored copies. The first entry is the default.
 */
public final class Stories {

    private Stories() {}

    public static final Story JACK = new Story("jack", "Jack and the Beanstalk",
            Storyboard.PAGES, Storyboard.ABOUT_ME_PORTRAIT);

    public static final Story RED = new Story("red", "Little Red Riding Hood",
            RedRidingHood.PAGES, RedRidingHood.ABOUT_ME_PORTRAIT);

    public static final List<Story> ALL = List.of(JACK, RED);

    /** @return the story with this id, or null if unknown. A blank id means the default (Jack). */
    public static Story find(String id) {
        if (id == null || id.isBlank()) return JACK;
        for (Story s : ALL) if (s.id().equals(id.strip())) return s;
        return null;
    }
}
