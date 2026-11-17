package com.beanstalk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Draws the pre-made page art of every story template once, and stores it with the template in Postgres
 * ({@link StoryTemplateStore}). Runs in the background after start and only draws pages that have no art yet, so a
 * restart costs nothing once all pages are drawn. Each page is a paid image call (40 for the two built-in stories).
 *
 * Only engines that can edit a person into a picture use template art ({@link ImageGenerator#supportsTemplateArt});
 * with any other engine this does nothing. Turn it off with TEMPLATE_ART=false.
 */
@Component
public class TemplateArtPainter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TemplateArtPainter.class);

    private final ImageGenerator generator;
    private final StoryTemplateStore templates;

    @Value("${beanstalk.template-art.enabled:true}") private boolean enabled;
    @Value("${beanstalk.image.seed}") private long seed;

    public TemplateArtPainter(ImageGenerator generator, StoryTemplateStore templates) {
        this.generator = generator;
        this.templates = templates;
    }

    /** True if books should be made by editing the person into stored template art. */
    public boolean active() { return enabled && generator.supportsTemplateArt(); }

    @EventListener(ApplicationReadyEvent.class)
    public void paintMissingInBackground() {
        if (!active()) return;
        Thread t = new Thread(this::paintMissing, "template-art");
        t.setDaemon(true);
        t.start();
    }

    /** Draws every template page that has no art yet. A page that fails is skipped and tried again on the next start. */
    void paintMissing() {
        for (Story story : templates.all()) {
            List<Integer> missing;
            try {
                missing = templates.pagesWithoutArt(story.id());
            } catch (Exception e) {
                log.error("Could not check the template art of {}", story.id(), e);
                continue;
            }
            if (missing.isEmpty()) continue;
            log.info("Drawing template art for {}: {} pages to do", story.id(), missing.size());
            int drawn = 0;
            for (Storyboard.Page page : story.allPages()) {
                if (!missing.contains(page.number())) continue;
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    byte[] art = Jpegs.ensure(generator.drawTemplate(page, seed));
                    templates.saveArt(story.id(), page.number(), art);
                    drawn++;
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    log.error("Could not draw template art for {} page {}; books use a from-scratch drawing for it until it exists",
                            story.id(), page.number(), e);
                }
            }
            log.info("Template art for {}: drew {} of {} missing pages", story.id(), drawn, missing.size());
        }
    }
}
