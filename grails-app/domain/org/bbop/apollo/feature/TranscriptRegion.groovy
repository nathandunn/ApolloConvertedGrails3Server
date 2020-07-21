package org.bbop.apollo.feature

import org.bbop.apollo.feature.BiologicalRegion

/**
 * Inherited from here:
 */
class TranscriptRegion extends BiologicalRegion{

    static constraints = {
    }

    static String cvTerm = "transcript_region"
    static String ontologyId = "SO:0000833"  // XX:NNNNNNN
    static String alternateCvTerm = "TranscriptRegion"
}
