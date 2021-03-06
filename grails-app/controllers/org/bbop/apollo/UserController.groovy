package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.crypto.hash.Sha256Hash
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
// import io.swagger.annotations.*
import org.springframework.http.HttpStatus

// @Api(value = "User Services: Methods for managing users")
@Transactional(readOnly = true)
class UserController {

    def permissionService
//    def preferenceService
    def userService
    def trackService


    // @ApiOperation(value = "Load all users and their permissions", nickname = "/user/loadUsers", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long / string", paramType = "query", example = "Optionally only user a specific userId as an integer database id or a username string")
            // , @ApiImplicitParam(name = "start", type = "long / string", paramType = "query", example = "(optional) Result start / offset")
            // , @ApiImplicitParam(name = "length", type = "long / string", paramType = "query", example = "(optional) Result length")
            // , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "(optional) Search name")
            // , @ApiImplicitParam(name = "sortColumn", type = "string", paramType = "query", example = "(optional) Sort column, default 'name'")
            // , @ApiImplicitParam(name = "sortAscending", type = "boolean", paramType = "query", example = "(optional) Sort column is ascending if true (default false)")
            // , @ApiImplicitParam(name = "omitEmptyOrganisms", type = "boolean", paramType = "query", example = "(optional) Omits empty organism permissions from return (default false)")
            // , @ApiImplicitParam(name = "showInactiveUsers", type = "boolean", paramType = "query", example = "(optional) Shows inactive users without permissions (default false)")
//    ])
    def loadUsers() {
        try {
            JSONObject dataObject = permissionService.handleInput(request, params)
            JSONArray returnArray = new JSONArray()
            // allow instructor see all the users
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.USER)) {
                render status: HttpStatus.UNAUTHORIZED
                return
            }
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)
            def allowableOrganisms = permissionService.getOrganisms(currentUser)

            List<String> allUserGroups = UserGroup.all

            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            if (!permissionService.isUserGlobalAdmin(currentUser)) {
                allUserGroups = allUserGroups.findAll() {
                    it.metadata == null || it.getMetaData(FeatureStringEnum.CREATOR.value) == (currentUser.id as String) || permissionService.isGroupAdmin(it, currentUser)
                }
            }
            List<String> allUserGroupsName = allUserGroups.name
            Map<String, List<UserOrganismPermission>> userOrganismPermissionMap = new HashMap<>()
            List<UserOrganismPermission> userOrganismPermissionList = UserOrganismPermission.findAllByOrganismInList(allowableOrganisms as List)
            for (UserOrganismPermission userOrganismPermission in userOrganismPermissionList) {
                List<UserOrganismPermission> userOrganismPermissionListTemp = userOrganismPermissionMap.get(userOrganismPermission.user.username)
                if (userOrganismPermissionListTemp == null) {
                    userOrganismPermissionListTemp = new ArrayList<>()
                }
                userOrganismPermissionListTemp.add(userOrganismPermission)
                userOrganismPermissionMap.put(userOrganismPermission.user.username, userOrganismPermissionListTemp)
            }

            def c = User.createCriteria()
            def offset = dataObject.start ?: 0
            def maxResults = dataObject.length ?: Integer.MAX_VALUE
            def searchName = dataObject.name ?: null
            def sortName = dataObject.sortColumn ?: 'name'
            def sortAscending = dataObject.sortAscending ?: true
            def showInactiveUsers = dataObject.showInactiveUsers ?: false
            def omitEmptyOrganisms = dataObject.omitEmptyOrganisms != null ? dataObject.omitEmptyOrganisms : false

            def users = c.list(max: maxResults, offset: offset) {
                if (dataObject.userId && dataObject.userId in Integer) {
                    eq('id', (Long) dataObject.userId)
                }
                if (dataObject.userId && dataObject.userId in String) {
                    eq('username', dataObject.userId)
                }
                if (searchName) {
                    or {
                        ilike('firstName', '%' + searchName + '%')
                        ilike('lastName', '%' + searchName + '%')
                        ilike('username', '%' + searchName + '%')
                    }
                }
                if(!showInactiveUsers){
                    eq('inactive',false)
                }
                if (sortName) {
                    switch (sortName) {
                        case "name":
                            order('firstName', sortAscending ? "asc" : "desc")
                            order('lastName', sortAscending ? "asc" : "desc")
                            break
                        case "email":
                            order('username', sortAscending ? "asc" : "desc")
                            break
                    }
                }
            }.unique { a, b ->
                a.id <=> b.id
            }

            int userCount = User.withCriteria {
                if (dataObject.userId && dataObject.userId in Integer) {
                    eq('id', (Long) dataObject.userId)
                }
                if (dataObject.userId && dataObject.userId in String) {
                    eq('username', dataObject.userId)
                }
                if(!showInactiveUsers){
                    eq('inactive',false)
                }
                if (searchName) {
                    or {
                        ilike('firstName', '%' + searchName + '%')
                        ilike('lastName', '%' + searchName + '%')
                        ilike('username', '%' + searchName + '%')
                    }
                }

            }.unique { a, b ->
                a.id <=> b.id
            }.size()

            users.each {
                def userObject = new JSONObject()

                userObject.userId = it.id
                userObject.username = it.username
                userObject.firstName = it.firstName
                userObject.lastName = it.lastName
                userObject.inactive = it.inactive ?: false
                Role role = userService.getHighestRole(it)
                userObject.role = role?.name


                JSONArray groupsArray = new JSONArray()
                List<String> groupsForUser = new ArrayList<>()
                // filter the userGroups to only show that the group that current instructor owned
                def userGroups = it.userGroups
                if (!permissionService.isUserGlobalAdmin(currentUser)) {
                    userGroups = userGroups.findAll() {
                        it.metadata == null || it.getMetaData(FeatureStringEnum.CREATOR.value) == (currentUser.id as String) || permissionService.isGroupAdmin(it, currentUser)
                    }
                }

                for (group in userGroups) {
                    JSONObject groupJson = new JSONObject()
                    groupsForUser.add(group.name)
                    groupJson.put("name", group.name)
                    groupsArray.add(groupJson)
                }
                userObject.groups = groupsArray


                JSONArray availableGroupsArray = new JSONArray()
                List<String> availableGroups = allUserGroupsName - groupsForUser
                for (group in availableGroups) {
                    JSONObject groupJson = new JSONObject()
                    groupJson.put("name", group)
                    availableGroupsArray.add(groupJson)
                }
                userObject.availableGroups = availableGroupsArray

                // organism permissions
                JSONArray organismPermissionsArray = new JSONArray()
                def userOrganismPermissionList3 = userOrganismPermissionMap.get(it.username)
                List<Long> organismsWithPermissions = new ArrayList<>()
                log.debug "number of groups for user: ${userOrganismPermissionList3?.size()} for ${it.username}"
                for (UserOrganismPermission userOrganismPermission in userOrganismPermissionList3) {
                    if (userOrganismPermission.organism in allowableOrganisms) {
                        JSONObject organismJSON = new JSONObject()
                        organismJSON.put("organism", userOrganismPermission.organism.commonName)
                        organismJSON.put("permissions", userOrganismPermission.permissions)
                        organismJSON.put("permissionArray", userOrganismPermission.permissionValues)
                        organismJSON.put("userId", userOrganismPermission.userId)
                        organismJSON.put("id", userOrganismPermission.id)
                        organismPermissionsArray.add(organismJSON)
                        organismsWithPermissions.add(userOrganismPermission.organism.id)
                    }
                }

                // if an organism has permissions
                Set<Organism> organismList = allowableOrganisms.findAll() {
                    !organismsWithPermissions.contains(it.id)
                }

                if (!omitEmptyOrganisms) {
                    for (Organism organism in organismList) {
                        JSONObject organismJSON = new JSONObject()
                        organismJSON.put("organism", organism.commonName)
                        organismJSON.put("permissions", "[]")
                        organismJSON.put("permissionArray", new JSONArray())
                        organismJSON.put("userId", it.id)
                        organismPermissionsArray.add(organismJSON)
                    }
                }


                userObject.organismPermissions = organismPermissionsArray

                // could probably be done in a separate object
                userObject.userCount = userCount
                userObject.searchName = searchName

                returnArray.put(userObject)
            }

            render returnArray as JSON
        }
        catch (Exception e) {
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            def error = [error: e.message]
            log.error error
            render error as JSON
        }
    }

    @Transactional
    def checkLogin() {
        def currentUser = permissionService.currentUser
//        preferenceService.evaluateSaves(true)

        // grab from session
        if (!currentUser) {
            if (permissionService.authenticateWithToken(request)) {
                currentUser = permissionService.currentUser
            }
        }

        if (currentUser) {
//            UserOrganismPreference userOrganismPreference
//            try {
//                // sets it by default
//                userOrganismPreference = preferenceService.getCurrentOrganismPreferenceInDB(params[FeatureStringEnum.CLIENT_TOKEN.value])
//            } catch (e) {
//                log.error(e)
//            }

            def userObject = userService.convertUserToJson(currentUser)

            if (!permissionService.hasAnyPermissions(currentUser) && !permissionService.isUserBetterOrEqualRank(currentUser, GlobalPermissionEnum.INSTRUCTOR)) {
                userObject.put(FeatureStringEnum.ERROR.value, "You do not have access to any organism on this server.  Please contact your administrator.")
            }
//            else if (userOrganismPreference) {
//                userObject.put("tracklist", userOrganismPreference.nativeTrackList)
//            }

            if(permissionService.isUserGlobalAdmin(currentUser)){
                String badCommonPath = trackService.checkCommonDataDirectory()
                log.debug "bad common path ${badCommonPath}"
                if(badCommonPath){
                    userObject.badCommonPath = badCommonPath
                }
            }

            render userObject as JSON
        } else {
            def userObject = new JSONObject()
            userObject.put(FeatureStringEnum.HAS_USERS.value, User.count > 0)
            render userObject as JSON
        }
    }


    @Transactional
    def updateTrackListPreference() {
        try {
            JSONObject dataObject = permissionService.handleInput(request, params)
            if (!permissionService.hasPermissions(dataObject, PermissionEnum.READ)) {
                render status: HttpStatus.UNAUTHORIZED
                return
            }
            log.info "updateTrackListPreference"

//            UserOrganismPreference uop = preferenceService.getCurrentOrganismPreferenceInDB(dataObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
//
//            uop.nativeTrackList = dataObject.get("tracklist")
//            uop.save(flush: true)
//            log.info "Added userOrganismPreference ${uop.nativeTrackList}"
            render new JSONObject() as JSON
        }
        catch (Exception e) {
            log.error "${e.message}"
            render([error: e.message]) as JSON
        }
    }


    // @ApiOperation(value = "Add user to group", nickname = "/user/addUserToGroup", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "group", type = "string", paramType = "query", example = "Group name")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User id")
            // , @ApiImplicitParam(name = "user", type = "email", paramType = "query", example = "User email/username (supplied if user id unknown)")
//    ])
    @Transactional
    def addUserToGroup() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        UserGroup userGroup = UserGroup.findByName(dataObject.group)
        User user = dataObject.userId ? User.findById(dataObject.userId) : User.findByUsername(dataObject.user)
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !permissionService.isGroupAdmin(userGroup, user)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }
        log.info "Adding user to group"

        user.addToUserGroups(userGroup)
        user.save(flush: true)
        log.info "Added user ${user.username} to group ${userGroup.name}"
        render new JSONObject() as JSON
    }

    // @ApiOperation(value = "Remove user from group", nickname = "/user/removeUserFromGroup", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "group", type = "string", paramType = "query", example = "Group name")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User id")
            // , @ApiImplicitParam(name = "user", type = "email", paramType = "query", example = "User email/username (supplied if user id unknown)")
//    ])
    @Transactional
    def removeUserFromGroup() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        UserGroup userGroup = UserGroup.findByName(dataObject.group)
        User user = dataObject.userId ? User.findById(dataObject.userId) : User.findByUsername(dataObject.user)
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !permissionService.isGroupAdmin(userGroup, user)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }
        log.info "Removing user from group"

        user.removeFromUserGroups(userGroup)
        user.save(flush: true)
        log.info "Removed user ${user.username} from group ${userGroup.name}"
        render new JSONObject() as JSON
    }

    // @ApiOperation(value = "Create user", nickname = "/user/createUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "email", type = "email", paramType = "query", example = "Email of the user to add")
            // , @ApiImplicitParam(name = "firstName", type = "string", paramType = "query", example = "First name of user to add")
            // , @ApiImplicitParam(name = "lastName", type = "string", paramType = "query", example = "Last name of user to add")
            // , @ApiImplicitParam(name = "metadata", type = "string", paramType = "query", example = "User metadata (optional)")
            // , @ApiImplicitParam(name = "role", type = "string", paramType = "query", example = "User role USER / ADMIN (optional: default USER) ")
            // , @ApiImplicitParam(name = "newPassword", type = "string", paramType = "query", example = "Password of user to add")
//    ])
    @Transactional
    def createUser() {
        try {
            log.info "Creating user"
            JSONObject dataObject = permissionService.handleInput(request, params)
            // allow instructor to create user
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.INSTRUCTOR)) {
                render status: HttpStatus.UNAUTHORIZED
                return
            }
            if (User.findByUsername(dataObject.email) != null) {
                JSONObject error = new JSONObject()
                error.put(FeatureStringEnum.ERROR.value, "User already exists. Please enter a new username")
                render error.toString()
                return
            }

            User user = new User(
                    firstName: dataObject.firstName
                    , lastName: dataObject.lastName
                    , username: dataObject.email
                    // set metadata got from dataObject, need to convert to String
                    , metadata: dataObject.metadata ? dataObject.metadata.toString() : null
                    , passwordHash: new Sha256Hash(dataObject.newPassword ?: dataObject.password).toHex()
            )
            user.save(insert: true)
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)
            // allow specify the metadata creator through webservice, if not specified, take current user as the creator
            if (!user.getMetaData(FeatureStringEnum.CREATOR.value)) {
                log.debug "creator does not exist, set current user as the creator"
                user.addMetaData(FeatureStringEnum.CREATOR.value, currentUser.id.toString())
            }
            String roleString = dataObject.role ?: GlobalPermissionEnum.USER.name()
            Role role = Role.findByName(roleString.toUpperCase())
            if (!role) {
                role = Role.findByName(GlobalPermissionEnum.USER.name())
            }
            log.debug "adding role: ${role}"
            user.addToRoles(role)
            role.addToUsers(user)
            role.save()
            user.save(flush: true)

            log.info "Added user ${user.username} with role ${role.name}"

            render new JSONObject() as JSON
        } catch (e) {
            log.error(e.fillInStackTrace())
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to add the user " + e.message)
            render jsonObject as JSON
        }

    }

    // @ApiOperation(value = "Inactivate user, removing all permsissions and setting flag", nickname = "/user/inactivateUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to delete")
            // , @ApiImplicitParam(name = "userToDelete", type = "email", paramType = "query", example = "Username (email) to inactivate")
//    ])
    @Transactional
    def inactivateUser() {
        try {
            log.info "Inactivating user"
            JSONObject dataObject = permissionService.handleInput(request, params)
            User user = null
            if (dataObject.has('userId')) {
                user = User.findById(dataObject.userId)
            }
            // to support the webservice
            if (!user && dataObject.has("userToDelete")) {
                user = User.findByUsername(dataObject.userToDelete)
            }

            if (!user) {
                def error = [error: 'The user does not exist']
                log.error(error.error)
                render error as JSON
                return
            }
            String creatorMetaData = user.getMetaData(FeatureStringEnum.CREATOR.value)
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)

            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            // allow delete a user if the current user is global admin or the current user is the creator of the user
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData)) {
                //render status: HttpStatus.UNAUTHORIZED
                def error = [error: 'not authorized to delete the user']
                log.error(error.error)
                render error as JSON
                return
            }

            user.userGroups.each { it ->
                it.removeFromUsers(user)
            }
            UserTrackPermission.deleteAll(UserTrackPermission.findAllByUser(user))
            UserOrganismPermission.deleteAll(UserOrganismPermission.findAllByUser(user))
//            UserOrganismPreference.deleteAll(UserOrganismPreference.findAllByUser(user))
            user.inactive = true

            log.info "Inactivated user ${user.username}"
            user.save(flush: true )

            render new JSONObject() as JSON
        } catch (e) {
            log.error(e.fillInStackTrace())
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to inactivate the user " + e.message+". Remove users and groups first.")
            render jsonObject as JSON
        }
    }

    // @ApiOperation(value = "Activate user", nickname = "/user/activateUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to delete")
            // , @ApiImplicitParam(name = "userToActivate", type = "email", paramType = "query", example = "Username (email) to inactivate")
//    ])
    @Transactional
    def activateUser() {
        try {
            log.info "Removing user"
            JSONObject dataObject = permissionService.handleInput(request, params)
            User user = null
            if (dataObject.has('userId')) {
                user = User.findById(dataObject.userId)
            }
            // to support the webservice
            if (!user && dataObject.has("userToActivate")) {
                user = User.findByUsername(dataObject.userToActivate)
            }

            if (!user) {
                def error = [error: 'The user does not exist']
                log.error(error.error)
                render error as JSON
                return
            }
            String creatorMetaData = user.getMetaData(FeatureStringEnum.CREATOR.value)
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)

            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            // allow delete a user if the current user is global admin or the current user is the creator of the user
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData)) {
                //render status: HttpStatus.UNAUTHORIZED
                def error = [error: 'not authorized to activate the user']
                log.error(error.error)
                render error as JSON
                return
            }

            user.inactive = false
            log.info "Activated user ${user.username}"
            user.save(flush: true )

            render new JSONObject() as JSON
        } catch (e) {
            log.error(e.fillInStackTrace())
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to activate the user " + e.message)
            render jsonObject as JSON
        }
    }

    // @ApiOperation(value = "Delete user", nickname = "/user/deleteUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to delete")
            // , @ApiImplicitParam(name = "userToDelete", type = "email", paramType = "query", example = "Username (email) to delete")
//    ])
    @Transactional
    def deleteUser() {
        try {
            log.info "Removing user"
            JSONObject dataObject = permissionService.handleInput(request, params)
            User user = null
            if (dataObject.has('userId')) {
                user = User.findById(dataObject.userId)
            }
            // to support the webservice
            if (!user && dataObject.has("userToDelete")) {
                user = User.findByUsername(dataObject.userToDelete)
            }

            if (!user) {
                def error = [error: 'The user does not exist']
                log.error(error.error)
                render error as JSON
                return
            }
            String creatorMetaData = user.getMetaData(FeatureStringEnum.CREATOR.value)
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)

            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            // allow delete a user if the current user is global admin or the current user is the creator of the user
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData)) {
                //render status: HttpStatus.UNAUTHORIZED
                def error = [error: 'not authorized to delete the user']
                log.error(error.error)
                render error as JSON
                return
            }

            user.userGroups.each { it ->
                it.removeFromUsers(user)
            }
            FeatureEvent.deleteAll(FeatureEvent.findAllByEditor(user))
            UserTrackPermission.deleteAll(UserTrackPermission.findAllByUser(user))
            UserOrganismPermission.deleteAll(UserOrganismPermission.findAllByUser(user))
//            UserOrganismPreference.deleteAll(UserOrganismPreference.findAllByUser(user))
            user.delete(flush: true)

            log.info "Removed user ${user.username}"

            render new JSONObject() as JSON
        } catch (e) {
            log.error(e.fillInStackTrace())
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to delete the user " + e.message)
            render jsonObject as JSON
        }
    }

    // @ApiOperation(value = "Update user", nickname = "/user/updateUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to update")
            // , @ApiImplicitParam(name = "email", type = "email", paramType = "query", example = "Email of the user to update")
            // , @ApiImplicitParam(name = "firstName", type = "string", paramType = "query", example = "First name of user to update")
            // , @ApiImplicitParam(name = "lastName", type = "string", paramType = "query", example = "Last name of user to update")
            // , @ApiImplicitParam(name = "metadata", type = "string", paramType = "query", example = "User metadata (optional)")
            // , @ApiImplicitParam(name = "newPassword", type = "string", paramType = "query", example = "Password of user to update")
//    ] )
    @Transactional
    def updateUser() {
        try {
            log.info "Updating user"
            JSONObject dataObject = permissionService.handleInput(request, params)
            // to support webservice, which either provides userId or email. Sometimes only email is provided.
            User user = null
            if (dataObject.has('userId')) {
                user = User.findById(dataObject.userId)
            }
            if (!user && dataObject.has("email")) {
                user = User.findByUsername(dataObject.email)
            }
            if (!user) {
                def error = [error: 'The user does not exist']
                log.error(error.error)
                render error as JSON
                return
            }
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)

            String creatorMetaData = user.getMetaData(FeatureStringEnum.CREATOR.value)
            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            // allow update a user if the current user is global admin or the current user is the creator of the user
            if (!permissionService.sameUser(dataObject, request) && !permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData)) {
                //render status: HttpStatus.UNAUTHORIZED
                def error = [error: 'not authorized to update the user']
                log.error(error.error)
                render error as JSON
                return
            }

            user.firstName = dataObject.firstName
            user.lastName = dataObject.lastName
            user.username = dataObject.email
            // if dataObject doesn't have metadata, then do not update the user metadata
            user.metadata = dataObject.metadata ? dataObject.metadata.toString() : user.metadata

            if (dataObject.newPassword) {
                user.passwordHash = new Sha256Hash(dataObject.newPassword).toHex()
            }
            // allow accessing from webservice
            // role may be not provided through webservice, so dataObject doesn't have 'role'
            String roleString = null
            if (dataObject.has('role')) {
                roleString = dataObject.role
            }
            Role currentRole = userService.getHighestRole(user)
            // if currentRole doesn't exist and roleString is not null, or currentRole is different than roleString
            if (!currentRole && roleString || (currentRole && roleString && !roleString.equalsIgnoreCase(currentRole.name))) {
                if (currentRole) {
                    user.removeFromRoles(currentRole)
                }
                Role role = Role.findByName(roleString.toUpperCase())
                user.addToRoles(role)
            }

            log.info "Updated user"
            user.save(flush: true)
            render new JSONObject() as JSON
        } catch (e) {
            log.error(e.fillInStackTrace())
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to update the user " + e.message)
            render jsonObject as JSON
        }

    }

    // @ApiOperation(value = "Get organism permissions for user, returns an array of permission objects", nickname = "/user/getOrganismPermissionsForUser", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to fetch")
//    ])
    def getOrganismPermissionsForUser() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        // to support webservice using either userId or username
        User user = dataObject.userId ? User.findById(dataObject.userId as Long) : User.findByUsername(dataObject.username)
        List<UserOrganismPermission> userOrganismPermissionList = UserOrganismPermission.findAllByUser(user)

        render userOrganismPermissionList as JSON
    }

    /**
     * Only changing one of the boolean permissions
     * @return
     */
    // @ApiOperation(value = "Update organism permissions", nickname = "/user/updateOrganismPermission", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")

            // , @ApiImplicitParam(name = "userId", type = "long", paramType = "query", example = "User ID to modify permissions for")
            // , @ApiImplicitParam(name = "user", type = "email", paramType = "query", example = "(Optional) user email of the user to modify permissions for if User ID is not provided")
            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "Name of organism to update")
            // , @ApiImplicitParam(name = "id", type = "long", paramType = "query", example = "Permission ID to update (can get from userId/organism instead)")

            // , @ApiImplicitParam(name = "ADMINISTRATE", type = "boolean", paramType = "query", example = "Indicate if user has administrative and all lesser (including user/group) privileges for the organism")
            // , @ApiImplicitParam(name = "WRITE", type = "boolean", paramType = "query", example = "Indicate if user has write and all lesser privileges for the organism")
            // , @ApiImplicitParam(name = "EXPORT", type = "boolean", paramType = "query", example = "Indicate if user has export and all lesser privileges for the organism")
            // , @ApiImplicitParam(name = "READ", type = "boolean", paramType = "query", example = "Indicate if user has read and all lesser privileges for the organism")
//    ])
    @Transactional
    def updateOrganismPermission() {
        log.info "Updating organism permissions"
        JSONObject dataObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(dataObject, PermissionEnum.ADMINISTRATE)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }
        UserOrganismPermission userOrganismPermission = UserOrganismPermission.findById(dataObject.id)

        User user = dataObject.userId ? User.findById(dataObject.userId as Long) : User.findByUsername(dataObject.user)

        log.debug "Finding organism by ${dataObject.organism}"
        Organism organism = permissionService.getOrganismForToken(dataObject.organism)
        if (!organism) {
            render([(FeatureStringEnum.ERROR.value): "Failed to find organism with ${dataObject.organism}"] as JSON)
            return
        }
        if (!user) {
            log.error("Failed to find user with ${dataObject.userId} OR ${dataObject.user}")
            render([(FeatureStringEnum.ERROR.value): "Failed to find user with ${dataObject.userId} OR ${dataObject.user}"] as JSON)
        }
        log.debug "found ${userOrganismPermission}"
        if (!userOrganismPermission) {
            userOrganismPermission = UserOrganismPermission.findByUserAndOrganism(user, organism)
        }

        if (!userOrganismPermission) {
            log.debug "creating new permissions! "
            userOrganismPermission = new UserOrganismPermission(
                    user: user
                    , organism: organism
                    , permissions: "[]"
            ).save(insert: true)
            log.debug "created new permissions! "
        }


        JSONArray permissionsArray = new JSONArray()
        if (dataObject.getBoolean(PermissionEnum.ADMINISTRATE.name())) {
            permissionsArray.add(PermissionEnum.ADMINISTRATE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.WRITE.name())) {
            permissionsArray.add(PermissionEnum.WRITE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.EXPORT.name())) {
            permissionsArray.add(PermissionEnum.EXPORT.name())
        }
        if (dataObject.getBoolean(PermissionEnum.READ.name())) {
            permissionsArray.add(PermissionEnum.READ.name())
        }

        if (permissionsArray.size() == 0) {
            userOrganismPermission.delete(flush: true)
            render userOrganismPermission as JSON
            return
        }

        userOrganismPermission.permissions = permissionsArray.toString()
        userOrganismPermission.save(flush: true)
        log.info "Updated organism permissions for user ${user.username} and organism ${organism.commonName} and permissions ${permissionsArray.toString()}"
        render userOrganismPermission as JSON


    }

    // @ApiOperation(value = "Get creator metadata for user, returns creator userId as JSONObject", nickname = "/user/getUserCreator", httpMethod = "POST")
    // @ApiImplicitParams([
            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
            // , @ApiImplicitParam(name = "email", type = "email", paramType = "query", example = "Email of the user")
//    ])
    def getUserCreator() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN)) {
            def error = [error: 'not authorized to view the metadata']
            log.error(error.error)
            render error as JSON
            return
        }
        User user = User.findByUsername(dataObject.email)
        if (!user) {
            def error = [error: 'The user does not exist']
            log.error(error.error)
            render error as JSON
            return
        }
        JSONObject metaData = new JSONObject()
        metaData.creator = user.getMetaData(FeatureStringEnum.CREATOR.value)
        render metaData as JSON

    }

}
