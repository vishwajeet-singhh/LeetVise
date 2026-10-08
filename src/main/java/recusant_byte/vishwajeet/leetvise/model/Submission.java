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
 * One submission from your LeetCode history = one row of the "Submissions" sheet.
 * {@code date} is epoch seconds; {@code status} is LeetCode's label, e.g. "Accepted" or "Wrong Answer".
 */
public record Submission(long id, long date, String slug, String title, String status, String language,
                         String runtime, String memory) {

    public static final String ACCEPTED = "Accepted";

    public boolean accepted() {
        return ACCEPTED.equals(status);
    }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
