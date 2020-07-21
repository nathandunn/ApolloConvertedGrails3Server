package org.bbop.apollo.feature

import org.bbop.apollo.feature.BiologicalRegion

/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class Gene extends BiologicalRegion{


    static constraints = {
    }

    static String cvTerm = "gene"
    static String ontologyId = "SO:0000704"// XX:NNNNNNN
    static String alternateCvTerm = "Gene"


}
