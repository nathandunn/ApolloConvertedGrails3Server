package org.bbop.apollo.event

import org.grails.web.json.JSONObject
import org.bbop.apollo.Sequence

/**
 * Created by ndunn on 10/29/14.
 */
class AnnotationEvent {

    JSONObject features
    Sequence sequence
    Operation operation
    boolean sequenceAlterationEvent
    String username
    // toplevel feature?

//    public AnnotationEvent(Object features,Sequence sequence,Operation operation){
////        super(features)
//        this.sequence = sequence
//        this.operation = operation
//    }

    public enum Operation {
        ADD,
        DELETE,
        UPDATE,
        ERROR
    }

}
