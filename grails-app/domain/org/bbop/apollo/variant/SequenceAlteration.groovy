package org.bbop.apollo.variant

import org.bbop.apollo.feature.SequenceFeature

class SequenceAlteration extends SequenceFeature{


    String alterationResidue

    static String cvTerm  = "sequence_alteration"
    static String ontologyId = "SO:0001059"
    static String alternateCvTerm = "SequenceAlteration"

    static constraints = {
        alterationResidue nullable: true
    }

    static mapping = {
        alleles cascade: 'all-delete-orphan'
        variantInfo cascade: 'all-delete-orphan'
    }

    static hasMany = [
            alleles: Allele,
            variantInfo: VariantInfo
    ]

    /** Get the offset added by the sequence alteration.
     *
     * @return Offset added by the sequence alteration
     */
    int getOffset() {
        return 0
    }
}