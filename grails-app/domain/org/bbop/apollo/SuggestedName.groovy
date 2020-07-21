package org.bbop.apollo

import org.bbop.apollo.attributes.FeatureType

class SuggestedName {

    static constraints = {
        name nullable: false
        metadata nullable: true
    }

    String name
    String metadata

    static hasMany = [
            featureTypes: FeatureType
    ]
}
