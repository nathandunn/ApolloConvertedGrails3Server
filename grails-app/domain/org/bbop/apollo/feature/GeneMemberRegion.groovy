package org.bbop.apollo.feature

import org.bbop.apollo.feature.BiologicalRegion

/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class GeneMemberRegion extends BiologicalRegion{

    static constraints = {
    }

    static String cvTerm = "gene_member_region"
    static String ontologyId = "SO:0000831"// XX:NNNNNNN
    static String alternateCvTerm = "GeneMemberRegion"
}
