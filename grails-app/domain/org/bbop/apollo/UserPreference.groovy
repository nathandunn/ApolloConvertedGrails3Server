package org.bbop.apollo

import org.bbop.apollo.preference.Preference
import org.bbop.apollo.user.User

class UserPreference extends Preference{

    static constraints = {
        user nullable: false
    }

    User user
}
