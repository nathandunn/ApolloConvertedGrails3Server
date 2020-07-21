package org.bbop.apollo

import org.bbop.apollo.variant.SequenceAlteration

class VariantInfo {

    String tag
    String value
    SequenceAlteration variant

    static constraints = {
        value nullable: true
    }

    static mapping = {
        value type: 'text'
    }

    static belongsTo = [
            SequenceAlteration
    ]
}
