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
 * In-flight jobs live in memory only; the child's photo is never written to disk. A finished book is saved
 * to the signed-in account's folder ({@link BookStore}) so it can be downloaded again after signing back in.
 */
@Service
public class BookService {

    public enum State { AWAITING_PAYMENT, RUNNING, DONE, FAILED }

    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final String owner;     // Google account id (sub) that started this book
        public final String name;      // child's name, used for the download file name
        public volatile State state = State.AWAITING_PAYMENT;
        public volatile int pagesDone = 0;
        public volatile String error;
        public volatile byte[] pdf;
        public final long createdAt = System.currentTimeMillis();
        // Kept in memory until the book is paid for, then handed to the generator.
        volatile byte[] refJpeg;
        volatile String looks;
        volatile String details;
        // Billable API calls this book needed, for the average-cost figure.
        final java.util.concurrent.atomic.AtomicInteger imageCalls = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger verifyCalls = new java.util.concurrent.atomic.AtomicInteger();

        Job(String owner, String name) { this.owner = owner; this.name = name; }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BookService.class);

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ImageGenerator generator;
    private final PdfComposer composer;
    private final FaceVerifier verifier;
    private final BookStore store;
    private final CostTracker costs;
    private final ExecutorService jobPool = Executors.newFixedThreadPool(2);
    private final ExecutorService pagePool;

    @Value("${beanstalk.image.seed}") private long seed;
    @Value("${beanstalk.image.max-attempts}") private int maxAttempts;

    public BookService(ImageGenerator generator, PdfComposer composer, FaceVerifier verifier, BookStore store,
                       CostTracker costs, @Value("${beanstalk.image.parallelism}") int parallelism) {
        this.generator = generator;
        this.composer = composer;
        this.verifier = verifier;
        this.store = store;
        this.costs = costs;
        this.pagePool = Executors.newFixedThreadPool(parallelism);
    }

    public boolean usesPhoto() { return generator.usesPhoto(); }

    /** Validates the inputs and holds them in memory as an unpaid job. Nothing is generated until {@link #begin}. */
    public Job prepare(String owner, byte[] photo, String name, String details, String appearance) throws Exception {
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
                throw new IllegalArgumentException("Please describe how the child looks (hair, eyes, skin tone).");
            refJpeg = hasPhoto ? normalisePhoto(photo) : null;   // only used by the optional verifier
            looks = "with " + appearance.strip();
        }
        Job job = new Job(owner, name);
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
            List<Storyboard.Page> all = new ArrayList<>(Storyboard.PAGES);
            all.add(Storyboard.ABOUT_ME_PORTRAIT);

            List<Future<byte[]>> futures = new ArrayList<>();
            for (Storyboard.Page p : all) {
                futures.add(pagePool.submit(() -> {
                    byte[] img = generateWithRetry(job, p, refJpeg, looks);
                    job.pagesDone++;
                    return img;
                }));
            }
            List<byte[]> images = new ArrayList<>();
            for (Future<byte[]> f : futures) images.add(f.get());   // order preserved: page 1..20

            byte[] pdf = composer.compose(images, name, details);
            try {
                store.save(job.owner, job.id, name, pdf, thumbnail(images.get(0)));
            } catch (Exception e) {
                log.error("Could not save book {} to storage; keeping it in memory only", job.id, e);
                job.pdf = pdf;      // the user can still download it now, it just won't be in their library
            }
            job.state = State.DONE;
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
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                byte[] img = generator.generate(p, refJpeg, looks, seed + attempt);
                lastImg = img;
                job.imageCalls.incrementAndGet();
                boolean others = p.number() == 1 || p.number() == 14 || p.number() == 19;
                if (refJpeg == null) return img;
                if (verifier.active()) job.verifyCalls.incrementAndGet();
                if (verifier.sameChild(refJpeg, img, others)) return img;
            } catch (Exception e) {
                last = e;
                Thread.sleep(1500L * (attempt + 1));
            }
        }
        if (lastImg != null) return lastImg;   // checker kept saying no; ship best effort rather than fail the book
        throw new RuntimeException("Page " + p.number() + " failed: " + rootMessage(last), last);
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
