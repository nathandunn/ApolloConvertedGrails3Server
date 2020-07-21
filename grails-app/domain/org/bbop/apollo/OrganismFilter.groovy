package org.bbop.apollo

import org.bbop.apollo.organism.Organism

abstract class OrganismFilter {

    Organism organism

    static constraints = {
        organism nullable: false
    }
}
