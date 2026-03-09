package com.solbeg.sas.perfmgmnt.service.rag;

/**
 * Enumeration of query types for RAG processing.
 * Different query types may require different retrieval strategies.
 */
public enum QueryType {
    /**
     * General queries that require broad document search and keyword matching.
     */
    GENERAL,

    /**
     * Definition queries that benefit from glossary-focused retrieval.
     */
    DEFINITION,

    /**
     * Time-related queries that focus on dates, deadlines, schedules, and periods.
     */
    TIME_RELATED,

    /**
     * Support queries that focus on errors, bugs, and other technical issues.
     */
    SUPPORT,

    /**
     * Inbox queries about notifications, requests
     */
    INBOX,

    /**
     * Peer assignment queries about selecting/finding peers for assessment
     */
    PEERS,

    /**
     * Review/Sync-up process queries about how to schedule, launch, start, create, or request reviews
     */
    REVIEW_PROCESS,

    /**
     * Queries about reminding peers to submit their review forms
     */
    REMIND_PEERS,

    /**
     * Queries about Growth Plan, tasks, progress, statuses, creating/updating/cancelling tasks
     */
    GROWTH_PLAN
}
