package org.bbop.apollo.variant

import org.bbop.apollo.VariantInfo

class SNV extends Substitution {

    static String cvTerm  = "SNV"
    static String ontologyId = "SO:0001483"
    static String alternateCvTerm = "single nucleotide variant"

    static hasMany = [
            alleles: Allele,
            variantInfo: VariantInfo
    ]

}
