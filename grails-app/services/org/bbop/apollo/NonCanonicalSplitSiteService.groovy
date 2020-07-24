package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.NonCanonicalFivePrimeSpliceSite
import org.bbop.apollo.feature.NonCanonicalThreePrimeSpliceSite
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.variant.SequenceAlterationArtifact

//@GrailsCompileStatic
@Transactional
class NonCanonicalSplitSiteService {

    def featureRelationshipService
    def transcriptService
    def featureService
    def sequenceService

    /** Delete an non canonical 5' splice site.  Deletes both the transcript -> non canonical 5' splice site and
     *  non canonical 5' splice site -> transcript relationships.
     *
     * @param nonCanonicalFivePrimeSpliceSite - NonCanonicalFivePrimeSpliceSite to be deleted
     */
    void deleteNonCanonicalFivePrimeSpliceSite(Transcript transcript, NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {

        featureRelationshipService.deleteChildrenForTypes(transcript, NonCanonicalFivePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalFivePrimeSpliceSite, Transcript.ontologyId)
        nonCanonicalFivePrimeSpliceSite.delete(flush: true)
    }

    void deleteNonCanonicalThreePrimeSpliceSite(Transcript transcript, NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {
        featureRelationshipService.deleteChildrenForTypes(transcript, NonCanonicalThreePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalThreePrimeSpliceSite, Transcript.ontologyId)
        nonCanonicalThreePrimeSpliceSite.delete(flush: true)
    }

    /** Delete all non canonical 5' splice site.  Deletes all transcript -> non canonical 5' splice sites and
     *  non canonical 5' splice sites -> transcript relationships.
     *
     */
    void deleteAllNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            deleteNonCanonicalFivePrimeSpliceSite(transcript, spliceSite);
        }
    }

    /** Retrieve all the non canonical 5' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 5' splice sites.  Non canonical 5' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 5' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 5' splice sites associated with this transcript
     */
    Collection<NonCanonicalFivePrimeSpliceSite> getNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        return (Collection<NonCanonicalFivePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript, NonCanonicalFivePrimeSpliceSite.ontologyId)
    }

    /** Retrieve all the non canonical 3' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 3' splice sites.  Non canonical 3' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 3' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 3' splice sites associated with this transcript
     */
    Collection<NonCanonicalThreePrimeSpliceSite> getNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
//        return (Collection<NonCanonicalThreePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript,FeatureStringEnum.NONCANONICALTHREEPRIMESPLICESITE)
        return (Collection<NonCanonicalThreePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript, NonCanonicalThreePrimeSpliceSite.ontologyId)
    }

    /** Delete all non canonical 3' splice site.  Deletes all transcript -> non canonical 3' splice sites and
     *  non canonical 3' splice sites -> transcript relationships.
     *
     */
    void deleteAllNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
//            featureRelationshipService.deleteRelationships(transcript,NonCanonicalThreePrimeSpliceSite.ontologyId,Transcript.ontologyId)
            deleteNonCanonicalThreePrimeSpliceSite(transcript, spliceSite)
        }
    }


    void findNonCanonicalAcceptorDonorSpliceSites(Transcript transcript) {

        transcript.attach()

        deleteAllNonCanonicalFivePrimeSpliceSites(transcript)
        deleteAllNonCanonicalThreePrimeSpliceSites(transcript)

        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        int fmin = transcript.getFeatureLocation().fmin
        int fmax = transcript.getFeatureLocation().fmax
        Sequence sequence = transcript.featureLocations.first().to
        Strand strand = transcript.getFeatureLocation().strand == -1 ? Strand.NEGATIVE : Strand.POSITIVE

        println "finding donor sites ${transcript as JSON}"
        String residues = sequenceService.getGenomicResiduesFromSequenceWithAlterations(sequence, fmin, fmax, strand);
        println "found RESIDUES ${residues}"

        if (transcript.getStrand() == -1) {
            residues = residues.reverse()
        }
        println "handled reversals"

        List<SequenceAlterationArtifact> sequenceAlterationList = new ArrayList<>()
        sequenceAlterationList.addAll(featureService.getAllSequenceAlterationsForFeature(transcript))
        println "add all alterations ${sequenceAlterationList}"

        println "iterating over exons ${exons}"

        for (Exon exon : exons) {
            int fivePrimeSpliceSitePosition = -1;
            int threePrimeSpliceSitePosition = -1;
            boolean validFivePrimeSplice = false;
            boolean validThreePrimeSplice = false;
            println "handling donor for exon ${exon}"
            for (String donor : SequenceTranslationHandler.getSpliceDonorSites()) {
                println "donor .. . ${donor}"
                for (String acceptor : SequenceTranslationHandler.getSpliceAcceptorSites()) {
                    println "acceptor .. . ${acceptor}"
                    int local11 = exon.fmin - donor.length() - transcript.fmin
                    int local22 = exon.fmin - transcript.fmin
                    int local33 = exon.fmax - transcript.fmin
                    int local44 = exon.fmax + donor.length() - transcript.fmin

                    int local1 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local11, sequenceAlterationList)
                    int local2 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local22, sequenceAlterationList)
                    int local3 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local33, sequenceAlterationList)
                    int local4 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local44, sequenceAlterationList)


                    if (exon.featureLocation.getStrand() == -1) {
                        int tmp1 = local1
                        int tmp2 = local2
                        local1 = local3
                        local2 = local4
                        local3 = tmp1
                        local4 = tmp2
                    }
                    if (local1 >= 0 && local2 < residues.length()) {
                        String acceptorSpliceSiteSequence = residues.substring(local1, local2)
                        acceptorSpliceSiteSequence = transcript.getStrand() == -1 ? acceptorSpliceSiteSequence.reverse() : acceptorSpliceSiteSequence
                        println "acceptor ${local1} ${local2} ${acceptorSpliceSiteSequence} ${acceptor}"
                        if (acceptorSpliceSiteSequence.toLowerCase() == acceptor) {
                            validThreePrimeSplice = true
                        } else {
                            threePrimeSpliceSitePosition = exon.getStrand() == -1 ? local1 : local2;
                        }
                    }

                    if (local3 >= 0 && local4 < residues.length()) {
                        String donorSpliceSiteSequence = residues.substring(local3, local4)
                        donorSpliceSiteSequence = transcript.getStrand() == -1 ? donorSpliceSiteSequence.reverse() : donorSpliceSiteSequence
                        println "donor ${local3} ${local4} ${donorSpliceSiteSequence} ${donor}"
                        if (donorSpliceSiteSequence.toLowerCase() == donor) {
                            validFivePrimeSplice = true
                        } else {
                            fivePrimeSpliceSitePosition = exon.getStrand() == -1 ? local3 : local4;
                        }
                    }
                }
            }
            println "output for donor ${exon}"
            if (!validFivePrimeSplice && fivePrimeSpliceSitePosition != -1) {
                def loc = fivePrimeSpliceSitePosition + transcript.fmin
                println "adding a noncanonical five prime splice site at ${fivePrimeSpliceSitePosition} ${loc}"
                addNonCanonicalFivePrimeSpliceSite(transcript, createNonCanonicalFivePrimeSpliceSite(transcript, loc));
                println "ADDED a noncanonical five prime splice site at ${fivePrimeSpliceSitePosition} ${loc}"
            }
            if (!validThreePrimeSplice && threePrimeSpliceSitePosition != -1) {
                def loc = threePrimeSpliceSitePosition + transcript.fmin
                println "adding a noncanonical three prime splice site at ${threePrimeSpliceSitePosition} ${loc}"
                addNonCanonicalThreePrimeSpliceSite(transcript, createNonCanonicalThreePrimeSpliceSite(transcript, loc));
                println "ADDED a noncanonical three prime splice site at ${threePrimeSpliceSitePosition} ${loc}"
            }
        }

        println "finished exons ${exons}"

        println "handling 5'"
        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
        println "HANDLED 5'"
        println "handling 3'"
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
        println "HANDLED 3'"
    }

    /** Add a non canonical 5' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalFivePrimeSpliceSite - Non canonical 5' splice site to be added
     */
    void addNonCanonicalFivePrimeSpliceSite(Transcript transcript, NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {
//        CVTerm partOfCvterm = cvTermService.partOf

        // add non canonical 5' splice site
        println "adding ${transcript} and ${nonCanonicalFivePrimeSpliceSite}"
        FeatureRelationship fr = new FeatureRelationship(
//                type: cvTermService.partOf
            from: transcript
            , to: nonCanonicalFivePrimeSpliceSite
            , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save();
        println "ADDED ${transcript} and ${nonCanonicalFivePrimeSpliceSite}"
        transcript.addToParentFeatureRelationships(fr);
        println "adding ${transcript} to parent ${fr}"
        nonCanonicalFivePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    /** Add a non canonical 3' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalThreePrimeSpliceSite - Non canonical 3' splice site to be added
     */
    void addNonCanonicalThreePrimeSpliceSite(Transcript transcript, NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {

        // add non canonical 3' splice site
        FeatureRelationship fr = new FeatureRelationship(
//                type: cvTermService.partOf
            from: transcript
            , to: nonCanonicalThreePrimeSpliceSite
            , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save();
        transcript.addToParentFeatureRelationships(fr);
        nonCanonicalThreePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    private NonCanonicalFivePrimeSpliceSite createNonCanonicalFivePrimeSpliceSite(Transcript transcript, int position) {
        println "creating a non-caonical 5 splice site  ${transcript} ${position}"
        String uniqueName = transcript.getUniqueName() + "-non_canonical_five_prime_splice_site-" + position;
        NonCanonicalFivePrimeSpliceSite spliceSite = new NonCanonicalFivePrimeSpliceSite(
            uniqueName: uniqueName
            , isAnalysis: transcript.isAnalysis
            , isObsolete: transcript.isObsolete
            , name: uniqueName
        ).save()
        println "CREATED a non-canonical 5 splice site   ${spliceSite}"

        spliceSite.addToFeatureLocations(new FeatureLocation(
            strand: transcript.strand
            , to: transcript.featureLocation.to
            , fmin: position
            , fmax: position
            , from: spliceSite
        ).save());
        println "add FL ${spliceSite}"
        return spliceSite;
    }


    private NonCanonicalThreePrimeSpliceSite createNonCanonicalThreePrimeSpliceSite(Transcript transcript, int position) {
        String uniqueName = transcript.getUniqueName() + "-non_canonical_three_prime_splice_site-" + position;
        println "creating a non-caonical 3 splice site  ${transcript} ${position}"
        NonCanonicalThreePrimeSpliceSite spliceSite = new NonCanonicalThreePrimeSpliceSite(
            uniqueName: uniqueName
            , name: uniqueName
            , isAnalysis: transcript.isAnalysis
            , isObsolete: transcript.isObsolete
//                ,timeAccessioned: new Date()
        ).save()
        println "CXRAETED a non-caonical 3 splice site  ${transcript} ${position}"
        spliceSite.addToFeatureLocations(new FeatureLocation(
            strand: transcript.strand
            , to: transcript.featureLocation.to
            , fmin: position
            , fmax: position
            , from: spliceSite
        ).save());
        println "add FL for 3' ${spliceSite}"
        return spliceSite;
    }

}
