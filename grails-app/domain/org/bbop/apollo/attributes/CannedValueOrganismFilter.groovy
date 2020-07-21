package org.bbop.apollo.attributes

import org.bbop.apollo.OrganismFilter

class CannedValueOrganismFilter extends OrganismFilter {

    CannedValue cannedValue

    static constraints = {
        cannedValue nullable: false
    }
}
