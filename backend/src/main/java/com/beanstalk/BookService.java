package com.beanstalk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Orchestrates one book: normalise the photo ONCE, then generate all 20 illustrations
 * (every call gets the identical reference + seed + style string), then compose the PDF.
 * In-flight jobs live in memory only; the person's photo is never written to disk, but a copy is kept in the
 * account's character bible in Postgres ({@link CharacterBibleStore}). A finished book is saved
 * to the signed-in account's folder ({@link BookStore}) so it can be downloaded again after signing back in.
 */
@Service
public class BookService {

    public enum State { AWAITING_PAYMENT, RUNNING, DONE, FAILED }

    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final String owner;     // Google account id (sub) that started this book
        public final String name;      // the person's name, used for the download file name
        public final Story story;      // which built-in template this book uses
        public volatile State state = State.AWAITING_PAYMENT;
        public volatile int pagesDone = 0;
        public volatile String error;
        public volatile byte[] pdf;
        public final long createdAt = System.currentTimeMillis();
        public volatile long startedAt;    // when generation began (after payment)
        public volatile long finishedAt;   // when the PDF was ready; 0 while running
        // Kept in memory until the book is paid for, then handed to the generator.
        volatile byte[] refJpeg;
        volatile String looks;
        volatile String details;
        // Billable API calls this book needed, for the average-cost figure.
        final java.util.concurrent.atomic.AtomicInteger imageCalls = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger verifyCalls = new java.util.concurrent.atomic.AtomicInteger();

        Job(String owner, String name, Story story) { this.owner = owner; this.name = name; this.story = story; }

        /** Milliseconds from "generation started" to "PDF ready" (or to now, while still running). */
        public long elapsedMs() {
            if (startedAt == 0) return 0;
            return (finishedAt != 0 ? finishedAt : System.currentTimeMillis()) - startedAt;
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BookService.class);

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ImageGenerator generator;
    private final PdfComposer composer;
    private final FaceVerifier verifier;
    private final BookStore store;
    private final CostTracker costs;
    private final CharacterBibleStore bible;
    private final ExecutorService jobPool = Executors.newFixedThreadPool(2);
    private final ExecutorService pagePool;

    @Value("${beanstalk.image.seed}") private long seed;
    @Value("${beanstalk.image.max-attempts}") private int maxAttempts;
    /** After this many seconds a page that already has an image stops retrying, so a book finishes in about a minute. */
    @Value("${beanstalk.job.budget-seconds:45}") private int budgetSeconds;

    public BookService(ImageGenerator generator, PdfComposer composer, FaceVerifier verifier, BookStore store,
                       CostTracker costs, CharacterBibleStore bible,
                       @Value("${beanstalk.image.parallelism}") int parallelism) {
        this.generator = generator;
        this.bible = bible;
        this.composer = composer;
        this.verifier = verifier;
        this.store = store;
        this.costs = costs;
        this.pagePool = Executors.newFixedThreadPool(parallelism);
    }

    public boolean usesPhoto() { return generator.usesPhoto(); }

    /** Validates the inputs and holds them in memory as an unpaid job. Nothing is generated until {@link #begin}. */
    public Job prepare(String owner, byte[] photo, String name, String details, String appearance, Story story) throws Exception {
        boolean hasPhoto = photo != null && photo.length > 0;
        final byte[] refJpeg;
        final String looks;
        if (generator.usesPhoto()) {
            if (!hasPhoto) throw new IllegalArgumentException("A photo is required.");
            refJpeg = normalisePhoto(photo);         // validates it's a real image, strips EXIF, caps size
            looks = Storyboard.PHOTO_LOOKS;
        } else {
            // Text-only model: it cannot see a photo, so the typed description is the only identity signal.
            if (appearance == null || appearance.isBlank())
                throw new IllegalArgumentException("Please describe how you look (hair, eyes, skin tone).");
            refJpeg = hasPhoto ? normalisePhoto(photo) : null;   // only used by the optional verifier
            looks = "with " + appearance.strip();
        }
        if (refJpeg != null) {
            // The account's character bible keeps the reference photo (as the "child" slug, kept for stored data) so it can be reused and edited.
            try {
                bible.saveUserPhoto(owner, refJpeg);
            } catch (Exception e) {
                log.error("Could not save the reference photo to the character bible; continuing without it", e);
            }
        }
        Job job = new Job(owner, name, story);
        job.refJpeg = refJpeg;
        job.looks = looks;
        job.details = details;
        jobs.put(job.id, job);
        return job;
    }

    /** Starts generating a paid job. Safe to call twice: only the first call does anything. */
    public void begin(Job job) {
        synchronized (job) {
            if (job.state != State.AWAITING_PAYMENT) return;
            job.startedAt = System.currentTimeMillis();
            job.state = State.RUNNING;
        }
        final byte[] ref = job.refJpeg;
        final String looks = job.looks, details = job.details;
        job.refJpeg = null; job.looks = null; job.details = null;
        jobPool.submit(() -> run(job, ref, looks, job.name, details));
    }

    /** Only the account that started a book may see its job. */
    public Job get(String owner, String id) {
        Job j = jobs.get(id);
        return j != null && j.owner.equals(owner) ? j : null;
    }

    private void run(Job job, byte[] refJpeg, String looks, String name, String details) {
        try {
            List<Storyboard.Page> all = job.story.allPages();

            // All 20 pages are requested at once (pool size = beanstalk.image.parallelism), so the book takes about
            // as long as the slowest single page instead of 20 pages one after another.
            List<Future<byte[]>> futures = new ArrayList<>();
            for (Storyboard.Page p : all) {
                futures.add(pagePool.submit(() -> {
                    byte[] img = Jpegs.ensure(generateWithRetry(job, p, refJpeg, looks));
                    synchronized (job) { job.pagesDone++; }
                    return img;
                }));
            }
            List<byte[]> images = new ArrayList<>();
            for (Future<byte[]> f : futures) images.add(f.get());   // order preserved: page 1..20

            byte[] pdf = composer.compose(images, name, details, job.story);
            try {
                store.save(job.owner, job.id, name, pdf, thumbnail(images.get(0)), job.story.id());
            } catch (Exception e) {
                log.error("Could not save book {} to storage; keeping it in memory only", job.id, e);
                job.pdf = pdf;      // the user can still download it now, it just won't be in their library
            }
            job.finishedAt = System.currentTimeMillis();
            job.state = State.DONE;
            log.info("Book {} ({}) finished in {} ms: {} image calls, {} identity checks",
                    job.id, job.story.id(), job.elapsedMs(), job.imageCalls.get(), job.verifyCalls.get());
            costs.recordBook(job.imageCalls.get(), job.verifyCalls.get());
        } catch (Exception e) {
            job.error = rootMessage(e);
            job.state = State.FAILED;
        }
    }

    /** Same reference on every attempt; only the seed is nudged if the identity check rejects the result. */
    private byte[] generateWithRetry(Job job, Storyboard.Page p, byte[] refJpeg, String looks) throws Exception {
        Exception last = null;
        byte[] lastImg = null;
        int flagged = 0;     // how many times the image service's safety filter rejected this page
        long deadline = job.startedAt + budgetSeconds * 1000L;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0 && System.currentTimeMillis() >= deadline) break;   // out of time: keep what we have
            try {
                // A flagged page would be flagged again with the same prompt, so each retry is worded more gently.
                byte[] img = generator.generate(Softener.soften(p, flagged), refJpeg, looks, seed + attempt);
                lastImg = img;
                job.imageCalls.incrementAndGet();
                boolean others = job.story != Stories.JACK || p.number() == 1 || p.number() == 14 || p.number() == 19;   // other people on the page
                if (refJpeg == null) return img;
                if (verifier.active()) job.verifyCalls.incrementAndGet();
                if (verifier.samePerson(refJpeg, img, others)) return img;
            } catch (Exception e) {
                last = e;
                if (Softener.isFlagged(e)) {
                    flagged++;
                    log.warn("Book {} page {} ({}): the image service flagged the output (attempt {}); retrying with softer wording",
                            job.id, p.number(), job.story.id(), attempt + 1);
                } else {
                    Thread.sleep(400L * (attempt + 1));
                }
            }
        }
        if (lastImg != null) return lastImg;   // checker kept saying no; ship best effort rather than fail the book
        log.error("Book {} page {} ({}) failed", job.id, p.number(), job.story.id(), last);
        if (flagged > 0)
            throw new RuntimeException("The image service's safety filter rejected page " + p.number() + " (\"" + p.title()
                    + "\") of " + job.story.title() + " " + flagged + " times, even with gentler wording. "
                    + "If it keeps happening, try a different photo.");
        throw new RuntimeException("Page " + p.number() + " (" + p.title() + ") failed: " + rootMessage(last));
    }

    /** Small JPEG of page 1 for the "My books" shelf; null if it can't be made (the book is still saved). */
    static byte[] thumbnail(byte[] pageImage) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(pageImage));
            if (src == null) return null;
            double s = Math.min(1.0, 360.0 / src.getWidth());
            int w = Math.max(1, (int) Math.round(src.getWidth() * s)), h = Math.max(1, (int) Math.round(src.getHeight() * s));
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpeg", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] normalisePhoto(byte[] raw) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
        if (src == null) throw new IllegalArgumentException("Unsupported image. Please upload a JPG or PNG.");
        if (Math.min(src.getWidth(), src.getHeight()) < 256)
            throw new IllegalArgumentException("Photo is too small. Use at least 256 px on the short side.");
        double s = Math.min(1.0, 1024.0 / Math.max(src.getWidth(), src.getHeight()));
        int w = (int) Math.round(src.getWidth() * s), h = (int) Math.round(src.getHeight() * s);
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam prm = writer.getDefaultWriteParam();
        prm.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        prm.setCompressionQuality(0.92f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), prm);   // new image => no EXIF/GPS carried over
        } finally { writer.dispose(); }
        return out.toByteArray();
    }

    private static String rootMessage(Throwable t) {
        while (t != null && t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t == null ? "Unknown error" : String.valueOf(t.getMessage());
    }

    /** Drop finished jobs after 30 minutes so photos/PDFs don't accumulate in memory. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 300_000)
    public void sweep() {
        long now = System.currentTimeMillis();
        jobs.values().removeIf(j -> j.state != State.RUNNING
                && j.createdAt < now - (j.state == State.AWAITING_PAYMENT ? 120 : 30) * 60_000L);
    }

    @PreDestroy
    void shutdown() { jobPool.shutdownNow(); pagePool.shutdownNow(); }
}
