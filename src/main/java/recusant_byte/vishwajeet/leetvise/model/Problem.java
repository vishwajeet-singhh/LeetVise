/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.model;

/**
 * One solved problem = one row in the "Problems" sheet.
 * Times are epoch seconds; 0 means "never".
 */
public class Problem {

    private String slug = "";
    private String id = "";
    private String title = "";
    private String difficulty = "";
    private String tags = "";
    private String notes = "";
    private long firstSolved;
    private long lastSolved;
    private long lastRevised;
    private long nextReview;
    private int timesRevised;
    private int intervalDays;
    /** 0 = never rated, 1 = Again, 2 = Hard, 3 = Good, 4 = Easy */
    private int confidence;

    public Problem() { }

    public Problem(String slug) {
        this.slug = slug;
    }

    public String getUrl() {
        return "https://leetcode.com/problems/" + slug + "/";
    }

    /** Keep the earliest first-solve and the latest last-solve we've seen. */
    public void recordSolved(long first, long last) {
        if (first > 0 && (firstSolved == 0 || first < firstSolved)) firstSolved = first;
        if (last > lastSolved) lastSolved = last;
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public long getFirstSolved() { return firstSolved; }
    public void setFirstSolved(long firstSolved) { this.firstSolved = firstSolved; }
    public long getLastSolved() { return lastSolved; }
    public void setLastSolved(long lastSolved) { this.lastSolved = lastSolved; }
    public long getLastRevised() { return lastRevised; }
    public void setLastRevised(long lastRevised) { this.lastRevised = lastRevised; }
    public long getNextReview() { return nextReview; }
    public void setNextReview(long nextReview) { this.nextReview = nextReview; }
    public int getTimesRevised() { return timesRevised; }
    public void setTimesRevised(int timesRevised) { this.timesRevised = timesRevised; }
    public int getIntervalDays() { return intervalDays; }
    public void setIntervalDays(int intervalDays) { this.intervalDays = intervalDays; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
