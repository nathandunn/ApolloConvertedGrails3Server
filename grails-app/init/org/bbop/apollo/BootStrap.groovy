import grails.converters.JSON
import org.bbop.apollo.FeatureType
import org.bbop.apollo.Preference
import org.bbop.apollo.sequence.SequenceTranslationHandler


class BootStrap {

    def configWrapperService
    def grailsApplication
    def featureTypeService
    def domainMarshallerService
    def proxyService
    def userService
    def roleService
    def trackService
    def phoneHomeService


    def init = { servletContext ->
        println "Initializing..."
//        def dataSource = grailsApplication.config.dataSource
//        println "Datasource"
//        println "Url: ${dataSource.url}"
//        println "Driver: ${dataSource.driverClassName}"
//        println "Dialect: ${dataSource.dialect}"

        println "A"
        System.getenv().each {
            log.debug it.key + "->" + it.value
        }
        println "B"

        Preference preference = new Preference(
            clientToken: "abc123yyp",
            dateCreated: new Date(),
            lastUpdated: new Date(),
        ).save()
        println "B.1"

        domainMarshallerService.registerObjects()
        println "C"
        proxyService.initProxies()
        println "D"


        SequenceTranslationHandler.spliceDonorSites.addAll(configWrapperService.spliceDonorSites)
        println "E"

        SequenceTranslationHandler.spliceAcceptorSites.addAll(configWrapperService.spliceAcceptorSites)
        println "F"

        if(FeatureType.count==0){
            featureTypeService.stubDefaultFeatureTypes()
        }

        println "G"
        roleService.initRoles()
        println "H"

        def admin = grailsApplication.config?.apollo?.admin
        println "I"
        if(admin){
            println "J"
            userService.registerAdmin(admin.username,admin.password,admin.firstName,admin.lastName)
        }
        println "K"
        def adminUser = userService.registerAdmin("admin@local.host","password","Admin","User")
        println "adminuser ${adminUser as JSON}"


        def commandCommonDirectory = trackService.checkCommonDataDirectory()
        println commandCommonDirectory

//        phoneHomeService.pingServerAsync(org.bbop.apollo.PhoneHomeEnum.START.value)

    }
    def destroy = {
//        phoneHomeService.pingServer(org.bbop.apollo.PhoneHomeEnum.STOP.value)
    }
}
