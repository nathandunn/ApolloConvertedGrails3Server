package org.bbop.apollo

import org.grails.web.json.JSONObject

class MetricsController {

    // TODO: implement metrics again at some point
    // Note: this was originally from yammers
    def index() {
        JSONObject jsonObject = new JSONObject()
        jsonObject.put("version","")
        jsonObject.put("gauges","")
        jsonObject.put("counters","")
        jsonObject.put("histograms","")
        jsonObject.put("meters","")
        jsonObject.put("timers","org.bbop.apollo.AnnotationEditorController.annotationEditorTimer")
    }
}
