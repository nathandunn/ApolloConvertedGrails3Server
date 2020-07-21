package org.bbop.apollo.attributes

import org.bbop.apollo.OrganismFilter

class CannedCommentOrganismFilter extends OrganismFilter {

    CannedComment cannedComment

    static constraints = {
        cannedComment nullable: false
    }
}
