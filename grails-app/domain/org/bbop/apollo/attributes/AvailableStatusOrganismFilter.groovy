package org.bbop.apollo.attributes

import org.bbop.apollo.OrganismFilter

class AvailableStatusOrganismFilter extends OrganismFilter {

    AvailableStatus availableStatus

    static constraints = {
        availableStatus nullable: false
    }
}
