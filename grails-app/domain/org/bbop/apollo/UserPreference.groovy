package org.bbop.apollo

//import grails.gorm.annotation.Entity
//
//@Entity
class UserPreference extends Preference{

    static constraints = {
        user nullable: false
    }

    User user
}
