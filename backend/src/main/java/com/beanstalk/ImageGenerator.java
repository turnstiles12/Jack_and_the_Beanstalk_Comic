package com.beanstalk;

/**
 * Produces one page illustration (JPEG/PNG bytes) from the page's prompt and the single reference photo.
 * Implementations are selected with beanstalk.image.provider = openai | fal | mock.
 */
public interface ImageGenerator {
    byte[] generate(Storyboard.Page page, byte[] referenceJpeg, long seed) throws Exception;
}
