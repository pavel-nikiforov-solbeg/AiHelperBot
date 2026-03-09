package com.solbeg.sas.perfmgmnt.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for PDF user guide processing.
 * Contains chunking parameters and page references.
 */
@Getter
@Setter
public class GuideProperties {

    /**
     * Path to PDF user guide file on local filesystem
     */
    private String filePath;

    /**
     * URL to the online user guide document.
     * Stored as {@code "source"} metadata on each indexed document chunk.
     */
    private String guideUrl;

    /**
     * Page number references for specific sections in the guide
     */
    private PageReferences pages;

    /**
     * Size of text chunks for PDF processing (in characters)
     * Larger chunks provide more context but may dilute relevance
     */
    private int chunkSize;

    /**
     * Overlap between consecutive chunks (in characters)
     * Helps maintain context continuity across chunk boundaries
     */
    private int overlap;

    /**
     * Page number mappings for specific sections in the user guide.
     * Used for direct navigation and context enhancement.
     */
    @Getter
    @Setter
    public static class PageReferences {

        /**
         * Glossary section page number
         */
        private int glossary;

        /**
         * Support information page number
         */
        private int support;

        /**
         * Starting page for main content
         */
        private int start;

        /**
         * Inbox module documentation page number
         */
        private int inbox;

        /**
         * Remind submission process page number
         */
        private int remindSubmission;

        /**
         * Growth plan (employee section) page number
         */
        private int gpEmployee;

        /**
         * Growth plan (manager section) page number
         */
        private int gpManager;
    }
}
