package com.beanstalk;

/**
 * Produces one page illustration (JPEG/PNG bytes). Selected with beanstalk.image.provider =
 * cloudflare | openai | fal | fal-flux2 | mock.
 */
public interface ImageGenerator {

    /**
     * @param referenceJpeg the child's photo (null if {@link #usesPhoto()} is false)
     * @param looks         text that fills the {LOOKS} slot of the prompt
     */
    byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception;

    /**
     * True if this engine can work from pre-drawn template art: draw a page with a stand-in hero
     * ({@link #drawTemplate}) and later edit a real person into that picture ({@link #generateFromTemplate}).
     */
    default boolean supportsTemplateArt() { return false; }

    /** Draws a template page from its text alone, with a generic stand-in hero and no reference photo. */
    default byte[] drawTemplate(Storyboard.Page page, long seed) throws Exception {
        throw new UnsupportedOperationException("This image engine cannot draw template art");
    }

    /**
     * Makes a book page by putting the person in referenceJpeg into the pre-drawn templateArt of that page,
     * keeping the rest of the picture as it is.
     */
    default byte[] generateFromTemplate(Storyboard.Page page, byte[] templateArt, byte[] referenceJpeg, String looks, long seed)
            throws Exception {
        return generate(page, referenceJpeg, looks, seed);
    }

    /** False for text-only models (they never see the photo; the typed appearance description is used). */
    default boolean usesPhoto() { return true; }
}
