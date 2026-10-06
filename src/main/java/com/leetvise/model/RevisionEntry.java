package com.leetvise.model;

/** One row of the "Revision Log" sheet. {@code date} is epoch seconds. */
public record RevisionEntry(long date, String slug, String id, String title, String difficulty, String rating) { }
