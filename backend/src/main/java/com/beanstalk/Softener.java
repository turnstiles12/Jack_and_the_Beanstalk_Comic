package com.beanstalk;

/**
 * Cloudflare's output filter sometimes rejects a page ("Your output has been flagged"). The same prompt will be
 * flagged again, so a retry uses a gentler wording of the same scene. Level 1 swaps menacing words and weapons for
 * mild ones; level 2 also calms the emotions, level 3 makes the villain friendly. The story text is never changed.
 */
final class Softener {

    private Softener() {}

    private static final String[][] LEVEL1 = {
        {"sly toothy grin", "cheeky grin"}, {"toothy", "wide"}, {"slyly", "playfully"}, {"sly", "playful"},
        {"fierce but kind", "determined and kind"}, {"fierce", "determined"}, {"angry", "surprised"},
        {"small axe", "small wooden mallet"}, {"felling axe", "wooden walking staff"}, {"axe", "walking staff"},
        {"leaps out of the bed after them", "hops out of the bed"}, {"leaps", "hops"},
        {"looms", "stands"}, {"bursts through", "steps through"}, {"burst through", "step through"},
        {"scrambling", "hurrying"}, {"roared", "called"}, {"chase", "race"}, {"running away", "hurrying away"},
        {"tail between his legs", "a cheerful trot"},
    };

    private static final String[][] LEVEL2 = {
        {"startled", "curious"}, {"alarmed", "curious"}, {"worry", "curiosity"}, {"worried", "curious"},
        {"ducks behind a chair", "peeks over a chair"}, {"cheeky grin", "gentle smile"},
    };

    /** Level 3 only: the villain becomes friendly. */
    private static final String[][] LEVEL3 = {
        {"wolf shadow", "soft shadow"}, {"grey wolf", "friendly grey wolf"}, {"giant", "big friendly giant"}, {"huge", "big"},
    };

    /** @param level 0 = unchanged, 1 = softer, 2 = calmer, 3 = friendliest */
    static String soften(String scene, int level) {
        if (level <= 0) return scene;
        String s = scene;
        for (String[] r : LEVEL1) s = s.replace(r[0], r[1]);
        if (level >= 2) for (String[] r : LEVEL2) s = s.replace(r[0], r[1]);
        if (level >= 3) for (String[] r : LEVEL3) s = s.replace(r[0], r[1]);
        return s;
    }

    static Storyboard.Page soften(Storyboard.Page p, int level) {
        if (level <= 0) return p;
        return new Storyboard.Page(p.number(), p.title(), soften(p.scene(), level), p.quiet(), p.text(), p.zone());
    }

    static boolean isFlagged(Throwable t) {
        for (; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && m.toLowerCase().contains("flagged")) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
