package org.bbop.apollo.attributes

import org.bbop.apollo.OrganismFilter

class CannedKeyOrganismFilter extends OrganismFilter {

    CannedKey cannedKey

    static constraints = {
        cannedKey nullable: false
    }
}
