package org.bbop.apollo

import grails.gorm.transactions.Transactional

@Transactional(readOnly = true)
class ProxyService {

//    private final static List<String> defaultProxies = ["http://golr.berkeleybop.org/"]
    def grailsApplication

    /**
     * Looks through all proxies to return valid proxies
     *
     * @return
     */
    Proxy findProxyForUrl(String referenceUrl){
        Proxy proxy = Proxy.findByReferenceUrlAndActive(referenceUrl,true)
        if(!proxy){
            def proxyList = Proxy.findAllByReferenceUrl(referenceUrl,[sort:"fallbackOrder",order:"asc"])
            if(proxyList){
                return proxyList.first()
            }
        }
        return proxy
    }

    @Transactional
    def initProxies(){
        println "C.1"
        def proxies = grailsApplication.config.apollo.proxies
        println "C.2 ${proxies}"


        for(proxyConfig in proxies){
            println "C.3 ${proxyConfig}"
            def proxy = Proxy.findByReferenceUrlAndTargetUrl(proxyConfig.referenceUrl,proxyConfig.targetUrl)
            println "C.4 ${proxy}"

            if (proxy && proxyConfig.replace) {
                proxy.active= proxyConfig.active
                proxy.fallbackOrder= proxyConfig.fallbackOrder
                proxy.save(failOnError: false,insert: false)
            }
            if(!proxy){

                if(proxyConfig.replace){
                    def proxyToDelete = Proxy.findByFallbackOrderAndActive(proxyConfig.fallbackOrder as Integer,proxyConfig.active as Boolean)
                    if(proxyToDelete){
                       proxyToDelete.delete()
                    }
                }

                proxy = new Proxy(
                        referenceUrl: proxyConfig.referenceUrl
                        , targetUrl: proxyConfig.targetUrl
                        ,active: proxyConfig.active
                        ,fallbackOrder: proxyConfig.fallbackOrder
                ).save(failOnError: false,insert: true)

            }
        }
    }

    /**
     *
     * @param urlProxy
     * @return
     */
    @Transactional
    Proxy findDefaultProxy(String urlProxy) {
        if(defaultProxies.contains(urlProxy)){
            Proxy proxy = new Proxy(
                    referenceUrl: urlProxy
                    ,targetUrl: urlProxy
                    ,active: true
            ).save(failOnError: true)
            return proxy
        }
        return null
    }
}
