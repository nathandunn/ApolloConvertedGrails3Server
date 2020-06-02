package org.bbop.apollo

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import groovy.transform.CompileStatic
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
//import org.grails.boot.internal.EnableAutoConfiguration
//import org.springframework.context.annotation.ComponentScan
//import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
    info = @Info(
        title = "my app",
        version = "1.0",
        description = "my api",
        license = @License(name = "Apache 2.0", url = "http://foo.bar"),
        contact = @Contact(url = "http://something.com", name = "something", email = "something")
    )
)

//@Configuration
//@EnableAutoConfiguration
//@ComponentScan
@CompileStatic
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}