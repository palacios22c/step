package com.tyndalehouse.step.core.service.jsword.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.index.search.DefaultSearchModifier;
import org.crosswire.jsword.index.search.DefaultSearchRequest;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageTally;
import org.crosswire.jsword.passage.PassageTally.Order;
import org.crosswire.jsword.passage.RestrictionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tyndalehouse.step.core.exceptions.StepInternalException;
import com.tyndalehouse.step.core.models.OsisWrapper;
import com.tyndalehouse.step.core.models.search.SearchEntry;
import com.tyndalehouse.step.core.models.search.SearchResult;
import com.tyndalehouse.step.core.models.search.VerseSearchEntry;
import com.tyndalehouse.step.core.service.jsword.JSwordPassageService;
import com.tyndalehouse.step.core.service.jsword.JSwordSearchService;
import com.tyndalehouse.step.core.service.jsword.JSwordVersificationService;

/**
 * API to search across the data
 * 
 * @author chrisburrell
 * 
 */
@Singleton
public class JSwordSearchServiceImpl implements JSwordSearchService {
    private static final int MAX_RESULTS = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(JSwordSearchServiceImpl.class);
    private final JSwordVersificationService av11nService;
    private final JSwordPassageService jsword;

    /**
     * @param av11nService the versification service
     * @param jsword the jsword lookup service to retrieve the references
     */
    @Inject
    public JSwordSearchServiceImpl(final JSwordVersificationService av11nService,
            final JSwordPassageService jsword) {
        this.av11nService = av11nService;
        this.jsword = jsword;

    }

    @Override
    public SearchResult search(final String version, final String query, final boolean ranked) {
        final long start = System.currentTimeMillis();

        final DefaultSearchModifier modifier = new DefaultSearchModifier();
        modifier.setRanked(ranked);
        modifier.setMaxResults(MAX_RESULTS);

        final Book bible = this.av11nService.getBookFromVersion(version);

        try {
            final Key results = bible.find(new DefaultSearchRequest(query, modifier));
            LOGGER.debug("[{}] verses found.", results.getCardinality());

            if (ranked) {
                rankAndTrimResults(results, MAX_RESULTS);
            } else {
                trimResults(results, MAX_RESULTS);
            }
            LOGGER.debug("Trimmed down to [{}].", results.getCardinality());

            final long startRefs = System.currentTimeMillis();
            final List<SearchEntry> resultPassages = getPassagesForResults(bible, results);
            final long endRefs = System.currentTimeMillis();

            return getSearchResult(query, start, startRefs, endRefs, resultPassages);
        } catch (final BookException e) {
            throw new StepInternalException("Unable to search for " + query + " with Bible " + version, e);
        }
    }

    /**
     * Constructs the search result object
     * 
     * @param query the query that was run
     * @param start the start time
     * @param startRefs the start time of retrieving the references
     * @param resultPassages the resulting passages
     * @param endRefs the end time of looking up the references
     * @return the search result to be returned to the service caller
     */
    private SearchResult getSearchResult(final String query, final long start, final long startRefs,
            final long endRefs, final List<SearchEntry> resultPassages) {
        final SearchResult r = new SearchResult();
        r.setResults(resultPassages);

        final long end = System.currentTimeMillis();

        // set stats:
        r.setMaxReached(MAX_RESULTS == resultPassages.size());
        r.setQuery(query);
        r.setTimeTookTotal(end - start);
        r.setTimeTookToRetrieveScripture(endRefs - startRefs);
        return r;
    }

    /**
     * Looks up all passages represented by the key
     * 
     * @param bible the bible under examination
     * @param results the list of results
     * @return the list of entries found
     */
    private List<SearchEntry> getPassagesForResults(final Book bible, final Key results) {
        final List<SearchEntry> resultPassages = new ArrayList<SearchEntry>();
        final Iterator<Key> rangeIter = ((Passage) results).rangeIterator(RestrictionType.NONE);

        while (rangeIter.hasNext()) {
            final Key range = rangeIter.next();

            final OsisWrapper peakOsisText = this.jsword.peakOsisText(bible, range);
            resultPassages.add(new VerseSearchEntry(peakOsisText.getReference(), peakOsisText.getValue()));
        }
        return resultPassages;
    }

    /**
     * Trims results to the correct size
     * 
     * @param results the key to the search results
     * @param maxResults the number of results we want to keep
     */
    private void trimResults(final Key results, final int maxResults) {
        if (results instanceof Passage) {
            final Passage p = (Passage) results;
            p.trimRanges(maxResults, RestrictionType.CHAPTER);
        }
    }

    /**
     * Looks up the results in rank order
     * 
     * @param results the results
     * @return the time it took
     */
    private void rankAndTrimResults(final Key results, final int maxResults) {
        if (!(results instanceof PassageTally)) {
            throw new StepInternalException("Unable to retrieve in ranked order...");
        }

        final PassageTally tally = (PassageTally) results;
        tally.setOrdering(Order.TALLY);
        tally.trimRanges(maxResults, RestrictionType.NONE);
    }
}