package org.bbop.apollo.feature

import org.bbop.apollo.feature.GeneMemberRegion

/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class Transcript extends GeneMemberRegion{

    static constraints = {
    }

    static String cvTerm = "transcript"
    static String ontologyId = "SO:0000673"// XX:NNNNNNN
    static String alternateCvTerm = "Transcript"

}
