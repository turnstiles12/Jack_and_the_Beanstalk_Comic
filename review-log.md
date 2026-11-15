## Run 1 — 2026-11-15 — reviewer: Claude (instructor-run) — browser: Claude in Chrome (regular window, already signed in; not a private window)
Total: NOT PRODUCED — Jack PDF, sign-out/sign-in return and 400px width could not be measured (see "Not measured").
Measured so far: 46 of 90 measurable points (PDF 30/36 · UI 10/18 · Money 2/20 as written · Engineering 4/16)
Upload → PDF (Little Red Riding Hood): submit → Stripe → return 21:33:09, PDF link 21:34:51 = 101 s (target < 60 s)
Upload → PDF (Jack and the Beanstalk): started 21:40:05, FAILED ≈ 21:42:35 with "Page 2 (The Trade) failed: request timed out". No PDF.
Balance trail: n/a — app has no balance. Pay-per-book via Stripe Checkout (Sandbox), $0.99 per comic, 2 payments made (Red OK, Jack paid then failed).
Avg cost per book: $0.03 shown on the order page next to the $0.99 price. CostTracker.java counts image calls + identity-check calls incl. retries, priced at configured rate-card values (not billed amounts).
Secrets: none found in working tree, full git history, or frontend source. No deduction.

Not measured:
- Jack and the Beanstalk PDF (generation failed): page count, shared template with Red, cross-story face check, About Me.
- Sign out / sign back in / re-download earlier PDF (needs the owner's Google sign-in).
- Usable at ~400 px (window resize was accepted but the page still reported a 752 px viewport; no overflow seen at 752 px).
- Failure message when generation is refused for short balance (no balance exists).

Rubric lines that do not match this app (instructor to rule): funded balance, balance display, refund request/processing, transaction history. The app charges per book through Stripe, so these score 0 as written.

Findings by section
PDF (Red only): 19 story pages + About Me = 20 pages, rubric asks 20 + About Me (-4); no cover page (to-do.md agrees). Same stylized 3D character on every page; likeness to the photo is loose but consistent. Captions sit in a rounded box at top or bottom, no face covered on any of the 20 pages; box edge grazes wolf's ears on pp. 6, 12, 15 (seen at half size only). Captions are rendered type (crisp at zoom), legible, story in order, About Me shows name and details entered.
UI: progress bar with named stages ("Painting your pages: N of 20 done", "Putting the pages together into your PDF…"). No thumbnails appear while pages finish (0 images on the page until done). Buy button gives no feedback for ~7 s before Stripe opens, twice. Error screen names the failing page but not whether the customer was charged; "Try again" resets the form, so a retry means paying again.
Engineering: character bible stored in Neon with per-account copy (CharacterBibleStore.java). Retry loop exists (BookService.generateWithRetry) but the UI never shows attempts; max-attempts=4 (rubric cap is 3); when all attempts fail identity check it ships the LAST image, not the best. to-do.md exists with two items. review-log.md was empty before this entry.

Fix first:
1. Jack failed AFTER payment: no book, no refund or credit, error does not say whether you were charged, "Try again" means paying again. Add automatic retry of a timed-out page, and refund or re-run on failure (UI/Money/Engineering).
2. Speed: Red took 101 s, Jack ran >150 s then timed out; target is < 60 s. fal timeout is 60 s and the 45 s budget only stops retries, so one slow page stalls the whole book (Engineering, -6).
3. Red has 19 story pages; add the missing page (and the cover from to-do.md) so both stories are 20 + About Me (PDF, -4).
4. Show per-image attempt count and thumbnails as each page finishes; cap attempts at 3 and keep the best-scoring image, not the last (UI -3, Engineering -3).
5. Decide with the instructor whether the balance/refund/history model is required; if so, build it (Money, up to -18).
Also: add feedback on the Buy button while Stripe loads; to-do.md bug about the About Me textbox resizing is still open.

## Run 2 — Little Red Riding Hood only — 2026-11-15 — reviewer: Claude (instructor-run) — browser: Claude in Chrome (regular window, already signed in; not a private window)
Book reviewed: one PDF, "Mason", story "red", book id 4fd0a40c-23e4-41e4-8f79-c8705e676b4f, 2,838,990 bytes (HTTP 200, application/pdf, %PDF-1.6).
Test photo: a photo the builder did not develop with, supplied by the instructor. About Me text entered: "Loves hiking, coffee and long walks in the woods."
Total: NOT PRODUCED — Jack PDF, sign-out/sign-in return and 400 px width were not measured, so this log scores Red Riding Hood only.
Measured for this book: 46 of 90 measurable points across the whole run (see run entry in review-log.md). Lines below are scored on Red Riding Hood alone.

### Timing
Order submitted and paid on Stripe (Sandbox, 4242 test card): returned to site, generation started about 21:33:09 UTC
Progress: 0 of 20 at 4 s; 12 of 20 at 60 s; 14 of 20 at 74 s; 19 of 20 at 97 s
"Putting the pages together into your PDF…" at 21:34:51 UTC, then "Your comic is ready! Made in 101 seconds" and Download PDF at 21:34:51 UTC
Upload → PDF = 101 s. Target is under 60 s: missed by 41 s.
Download PDF button: worked, HTTP 200, valid PDF.
Bookshelf API after the run lists the book (name, story, title, createdAt), so it is saved to the account.

### Page-by-page (20 pages in the PDF: pp. 1–19 story, p. 20 About Me)
| Pg | Scene | Caption position | Faces clear? | Note |
|---|---|---|---|---|
| 1 | Mother packs the basket | bottom, over basket and path | yes | no cover page before this |
| 2 | Stay on the path | top, over trees | yes | |
| 3 | Into the forest | bottom, over path | yes | |
| 4 | Wolf appears behind oak | bottom, over Red's legs | yes | |
| 5 | "Where are you going?" | bottom, over path | yes | |
| 6 | Meadow of flowers | top | yes | box edge touches top of wolf's ears (half-size view only) |
| 7 | Picking flowers | top, over trees | yes | |
| 8 | Wolf at the door | bottom, over wolf's legs | yes | |
| 9 | Grandmother hides | bottom, over floor | yes | |
| 10 | Wolf in nightcap | top | yes | box edge near nightcap and ears |
| 11 | Red arrives | bottom, over legs and basket | yes | |
| 12 | "What big ears" | top | yes | box edge touches wolf's ear tips |
| 13 | "What big eyes" | bottom, over legs | yes | |
| 14 | "What big teeth" | bottom, over legs | yes | |
| 15 | The chase | top | yes | box edge near top of wolf's head |
| 16 | Woodcutter | bottom, over lower body | yes | |
| 17 | Wolf flees | bottom, over path | yes | wolf on roof, Red and woodcutter below |
| 18 | Grandmother climbs out | top | yes | box covers top of grandmother's hair bun, not her face |
| 19 | Tea time, "The End" | bottom, over table and treats | yes | |
| 20 | About Me | bottom, over boots | yes | name "Mason" and the details entered are shown |
No page has text over a face, so the 20-point cap on the PDF section does not apply. Pp. 6, 12 and 15 should be re-checked at full size.

### Rubric lines (Red Riding Hood only)
PDF
- 20 story pages + About Me: 0/4. The PDF has 19 story pages + About Me = 20 pages. The code confirms it (RedRidingHood.java defines pages 1–19 and ABOUT_ME_PORTRAIT as page 20).
- One shared 20-page template across both stories: NOT MEASURED (Jack PDF does not exist).
- Same child on every page: 6/8. Same stylized 3D hooded character with the same hair, face and smile on pp. 1, 3, 11 and 20 (p. 10 has no Red, so p. 11 was used). Likeness to the photo is loose, as to-do.md already says.
- One visual style: 4/4. Same glossy 3D animated look on every page.
- Text never covers a face or needed detail: 10/10 on this book, with the caveat above for pp. 6, 12, 15.
- Rendered type, not model lettering: 4/4. Zoomed on the About Me block: crisp, even letterforms; same font on every caption.
- Legible at 100%: 2/2. White bold type on a dark translucent box.
- Story in order and complete: 2/2. Runs mother → forest → wolf → cottage → woodcutter → tea, ends "The End." on p. 19.
- About Me has name and entered details: 2/2.
Subtotal measured for this book: 30 of 36.

UI (observed during this run)
- Progress bar naming the stage: 3/3. "Painting your pages: N of 20 done (Ns)" then "Putting the pages together into your PDF…".
- Thumbnails as each image finishes: 0/3. No images on the page until the book was done.
- Buy button feedback: the first click showed nothing for about 7 s before Stripe opened (seen twice).
- First-time clarity: 3/3. Three clearly labeled steps, Buy button greys out with a hint until photo, name and consent are done.

Engineering (observed during this run)
- Upload → PDF under 60 s: 0/6 (101 s).
- Regeneration loop visible, attempts capped at 3: 0/3. The UI shows pages done, not attempts per image. Code allows 4 attempts and keeps the last image rather than the best.
- Sign out/in and re-download: NOT MEASURED.

Money (observed during this run)
- One Stripe Checkout payment of $0.99 for this book. The app has no funded balance, refund or transaction history, so those rubric lines score 0 as written and need an instructor ruling.
- Cost tracking: $0.03 average per book shown on the order page beside the $0.99 price; counts image and identity-check calls including retries at configured rate-card prices.

### Not measured for this book
Sign-out/sign-in and re-download of this PDF; usability at ~400 px; comparison with a second story's template; text-over-face check at full size on pp. 6, 12, 15.

### Fix first (Red Riding Hood)
1. Speed: 101 s against a 60 s target. 12 of 20 pages were done at 60 s and the last few pages dragged on. The 45 s budget only stops retries, and the image call timeout is 60 s, so slow pages hold up the whole book (Engineering, -6).
2. Page count: add the missing 20th story page (and the cover from to-do.md) so the book is 20 story pages + About Me (PDF, -4).
3. Likeness: the character is consistent but only loosely resembles the photo; strengthen the face-lock reference or add a stricter identity check (PDF, up to -2).
4. Live thumbnails and attempt counts while pages finish (UI -3, Engineering -3).
5. Buy button needs a loading state while Stripe opens; re-check caption boxes on pp. 6, 12, 15 so they never clip the wolf's ears.