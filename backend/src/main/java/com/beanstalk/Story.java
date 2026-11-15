package com.beanstalk;

import java.util.ArrayList;
import java.util.List;

/**
 * One built-in storybook template: 19 story pages plus the page-20 "About Me" portrait.
 * Every story shares the same page layout (page numbers, text zones, panel sizes), so the PDF composer
 * and the progress UI treat them identically; only the scenes, the text and the hero's outfit differ.
 *
 * @param id      stable key used in the API and the database ("jack", "red")
 * @param title   book title shown in the UI, the PDF metadata and the download file name
 * @param pages   story pages 1-19
 * @param aboutMe page 20 portrait
 */
public record Story(String id, String title, List<Storyboard.Page> pages, Storyboard.Page aboutMe) {

    public static final int PAGE_COUNT = 20;

    public Story {
        pages = List.copyOf(pages);
        if (pages.size() != PAGE_COUNT - 1)
            throw new IllegalArgumentException(id + " needs " + (PAGE_COUNT - 1) + " story pages, has " + pages.size());
    }

    /** Pages 1-19 followed by the About Me portrait, in order. */
    public List<Storyboard.Page> allPages() {
        List<Storyboard.Page> all = new ArrayList<>(pages);
        all.add(aboutMe);
        return all;
    }

    /** "Jack-and-the-Beanstalk" style slug for download file names. */
    public String fileSlug() {
        return title.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
