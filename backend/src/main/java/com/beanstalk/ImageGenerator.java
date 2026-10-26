package com.beanstalk;

/**
 * Produces one page illustration (JPEG/PNG bytes). Selected with beanstalk.image.provider =
 * cloudflare | openai | fal | mock.
 */
public interface ImageGenerator {

    /**
     * @param referenceJpeg the child's photo (null if {@link #usesPhoto()} is false)
     * @param looks         text that fills the {LOOKS} slot of the prompt
     */
    byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception;

    /** False for text-only models (they never see the photo; the typed appearance description is used). */
    default boolean usesPhoto() { return true; }
}
