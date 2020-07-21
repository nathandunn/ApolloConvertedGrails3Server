package org.bbop.apollo

import org.bbop.apollo.feature.Feature

/**
 * Note: top-level in the sequence ontology
 */
class SequenceFeature extends Feature implements Ontological {

    static constraints = {
    }

    static String cvTerm = "sequence_feature"
    static String ontologyId = "SO:0000110"// XX:NNNNNNN
    static String alternateCvTerm = "SequenceFeature"

    // add convenience methods
}
