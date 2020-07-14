package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.Transactional
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.session.Session
import org.apache.shiro.subject.Subject
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.preference.OrganismDTO
import org.bbop.apollo.preference.SequenceDTO
import org.bbop.apollo.preference.UserDTO
import org.bbop.apollo.preference.UserOrganismPreferenceDTO
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import javax.servlet.http.HttpServletRequest

@Transactional
class PermissionService {

//    def preferenceService
    def configWrapperService


    def remoteUserAuthenticatorService
    def usernamePasswordAuthenticatorService


    boolean isUserBetterOrEqualRank(User user, GlobalPermissionEnum globalPermissionEnum) {
        // TODO: remove
        return true
        if (user != null) {
            for (Role role in user.roles) {
                if (role.rank >= globalPermissionEnum.rank) {
                    return true
                }
            }
        }
        return false
    }

    boolean isUserGlobalAdmin(User user) {
        return isUserBetterOrEqualRank(user, GlobalPermissionEnum.ADMIN)
    }

    boolean isAdmin() {
        // TODO: remove
        return true
        String currentUserName = SecurityUtils.subject.principal
        if (currentUserName) {
            User researcher = User.findByUsername(currentUserName)
            if (isUserGlobalAdmin(researcher)) {
                return true
            }
        }
        return false
    }

    boolean isGroupAdmin(UserGroup group, User user) {
        for (User u in group.admin) {
            if (user.id == u.id)
                return true
        }
        return false
    }

    List<Organism> getOrganisms(User user) {
        if (isUserGlobalAdmin(user)) {
            return Organism.listOrderByCommonName()
        }
        Set<Organism> organismList = new HashSet<>()
        for (UserOrganismPermission userPermission in UserOrganismPermission.findAllByUser(user)) {
            if (userPermission.permissions) {
                organismList.add(userPermission.organism)
            }
        }
        for (UserGroup userGroup in user?.userGroups) {
            organismList.addAll(getOrganismsForGroup(userGroup))
        }
        List<Organism> returnOrganismList = []
        for (Organism organism in organismList.sort() { a, b -> a.commonName <=> b.commonName }) {
            returnOrganismList.add(organism)
        }

        return returnOrganismList
    }

    List<Organism> getOrganismsWithMinimumPermission(User user, PermissionEnum permissionEnum) {
        if (isUserGlobalAdmin(user)) {
            return Organism.listOrderByCommonName()
        }
        Set<Organism> organismList = new HashSet<>()
        for (UserOrganismPermission userPermission in UserOrganismPermission.findAllByUser(user)) {
            if (userPermission.permissions) {
                organismList.add(userPermission.organism)
            }
        }
        for (UserGroup userGroup in user?.userGroups) {
            organismList.addAll(getOrganismsForGroup(userGroup))
        }
        List<Organism> returnOrganismList = []
        for (Organism organism in organismList.sort() { a, b -> a.commonName <=> b.commonName }) {
            PermissionEnum highestPermission = getOrganismPermissionsForUser(organism, currentUser).sort() { a, b -> a.rank <=> b.rank }.first()
            if (highestPermission.rank >= permissionEnum.rank) {
                returnOrganismList.add(organism)
            }
        }

        return returnOrganismList
    }

    Map<Organism, PermissionEnum> getOrganismsWithPermission(User user) {
        if (isUserGlobalAdmin(user)) {
            return Organism.listOrderByCommonName()
        }
        Set<Organism> organismList = new HashSet<>()
        for (UserOrganismPermission userPermission in UserOrganismPermission.findAllByUser(user)) {
            if (userPermission.permissions) {
                organismList.add(userPermission.organism)
            }
        }
        for (UserGroup userGroup in user?.userGroups) {
            organismList.addAll(getOrganismsForGroup(userGroup))
        }
        Map<Organism, PermissionEnum> returnOrganismMap = [:]
        for (Organism organism in organismList.sort() { a, b -> a.commonName <=> b.commonName }) {
            PermissionEnum highestPermission = getOrganismPermissionsForUser(organism, currentUser).sort() { a, b -> a.rank <=> b.rank }.first()
            returnOrganismMap.put(organism, highestPermission)
        }

        return returnOrganismMap
    }

    List<Organism> getOrganismsForGroup(UserGroup group) {
        if (isAdmin()) {
            return Organism.listOrderByCommonName()
        }
        List<Organism> organismList = new ArrayList<>()
        for (GroupOrganismPermission groupPermission in GroupOrganismPermission.findAllByGroup(group)) {
            // minimally, you should have at least one permission
            if (groupPermission.permissions) {
                organismList.add(groupPermission.organism)
            }
        }
        return organismList
    }


    static Collection<PermissionEnum> mergeOrganismPermissions(Collection<PermissionEnum> permissionsA, Collection<PermissionEnum> permissionsB) {
        Set<PermissionEnum> permissionEnums = new HashSet<>()
        permissionEnums.addAll(permissionsA)

        for (PermissionEnum permissionEnum in permissionsB) {
            permissionEnums.add(permissionEnum)
        }

        return permissionEnums
    }


    List<PermissionEnum> getOrganismPermissionsForUser(Organism organism, User user) {
        Set<PermissionEnum> permissions = new HashSet<>()
        if (isUserGlobalAdmin(user)) {
            permissions.addAll(PermissionEnum.ADMINISTRATE as List)
        }

        List<UserOrganismPermission> userPermissionList = UserOrganismPermission.findAllByOrganismAndUser(organism, user)
        for (UserOrganismPermission userPermission in userPermissionList) {
            JSONArray jsonArray = JSON.parse(userPermission.permissions) as JSONArray
            for (int i = 0; i < jsonArray.size(); i++) {
                String permission = jsonArray.getString(i)
                PermissionEnum permissionEnum = PermissionEnum.getValueForString(permission)
                permissions.add(permissionEnum)
            }
        }
        if (user != null) {
            for (UserGroup group in user.userGroups) {
                permissions = mergeOrganismPermissions(permissions, getOrganismPermissionsForUserGroup(organism, group))
            }
        } else {
            permissions.add(PermissionEnum.NONE)
        }


        return permissions as List

    }

    List<PermissionEnum> getOrganismPermissionsForUserGroup(Organism organism, UserGroup userGroup) {
        Set<PermissionEnum> permissions = new HashSet<>()

        List<GroupOrganismPermission> groupPermissionList = GroupOrganismPermission.findAllByOrganismAndGroup(organism, userGroup)
        for (GroupOrganismPermission groupPermission in groupPermissionList) {
            JSONArray jsonArray = JSON.parse(groupPermission.permissions) as JSONArray
            for (int i = 0; i < jsonArray.size(); i++) {
                String permission = jsonArray.getString(i)
                PermissionEnum permissionEnum = PermissionEnum.getValueForString(permission)
                permissions.add(permissionEnum)
            }
        }
        return permissions as List
    }


    void setOrganismPermissionsForUser(List<PermissionEnum> permissions, Organism organism, User user, String token) {

        UserOrganismPermission userOrganismPermission = UserOrganismPermission.findByOrganismAndUser(organism, user)
        if (!userOrganismPermission) {
            new UserOrganismPermission(
                organism: organism
                , permissions: generatePermissionString(permissions)
                , user: user
                , token: token
            ).save(insert: true)
        } else {
            userOrganismPermission.permissions = generatePermissionString(permissions)
            userOrganismPermission.save()
        }

    }

    void setOrganismPermissionsForUserGroup(List<PermissionEnum> permissions, Organism organism, UserGroup group, String token) {

        GroupOrganismPermission groupOrganismPermission = GroupOrganismPermission.findByOrganismAndGroup(organism, group)
        if (!groupOrganismPermission) {
            new GroupOrganismPermission(
                organism: organism
                , permissions: generatePermissionString(permissions)
                , group: group
                , token: token
            ).save(insert: true)
        } else {
            groupOrganismPermission.permissions = generatePermissionString(permissions)
            groupOrganismPermission.save()
        }
    }

    private static String generatePermissionString(List<PermissionEnum> permissionEnums) {
        JSONArray jsonArray = new JSONArray()
        for (PermissionEnum permissionEnum in permissionEnums) {
            jsonArray.add(permissionEnum.name())
        }
        return jsonArray.toString()
    }

    /**
     * Get all of the highest organism permissions for a user
     * @param user
     * @return
     */
    Map<String, Integer> getPermissionsForUser(User user) {
        Map<String, Integer> returnMap = new HashMap<>()
        if (!user) {
            log.warn("No user provided, please log in")
            return returnMap
        }
        returnMap.put(user.username, 0)
        Organism.all.each { organism ->
            List<PermissionEnum> permissionEnums = getOrganismPermissionsForUser(organism, user)
            int highestValue = findHighestEnumValue(permissionEnums)
            if (highestValue > returnMap.get(user.username)) {
                returnMap.put(user.username, highestValue)
            }
        }

        return returnMap
    }


    PermissionEnum findHighestEnum(List<PermissionEnum> permissionEnums) {
        PermissionEnum highestValue = PermissionEnum.NONE
        permissionEnums.each { it ->
            highestValue = it.rank > highestValue.rank ? it : highestValue
        }

        return highestValue
    }

    int findHighestEnumValue(List<PermissionEnum> permissionEnums) {
        int highestValue = -1
        permissionEnums.each { it ->
            highestValue = it.value > highestValue ? it.value : highestValue
        }

        return highestValue
    }

    JSONObject copyValue(FeatureStringEnum featureStringEnum, JSONObject fromJSON, JSONObject toJSON) {
        if (fromJSON.containsKey(featureStringEnum.value)) {
            toJSON.put(featureStringEnum.value, fromJSON.getString(featureStringEnum.value))
        } else {
            log.info "No ${featureStringEnum.value} to copy from ${fromJSON}"
        }
        return toJSON
    }

    /**
     * Copies values relevant to request
     * @param fromJSON
     * @param toJSON
     * @return
     */
    JSONObject copyRequestValues(JSONObject fromJSON, JSONObject toJSON) {
        copyValue(FeatureStringEnum.USERNAME, fromJSON, toJSON)
        copyValue(FeatureStringEnum.CLIENT_TOKEN, fromJSON, toJSON)
        copyValue(FeatureStringEnum.ORGANISM, fromJSON, toJSON)
        return toJSON
    }

    def getOrganismsForCurrentUser(JSONObject jsonObject) {
        User thisUser = getCurrentUser(jsonObject)
        if (thisUser) {
            return getOrganisms(thisUser) as List<Organism>
        }
        return []
    }

    static String getSequenceNameFromInput(JSONObject inputObject) {
        String trackName = null
        if (inputObject.has(FeatureStringEnum.SEQUENCE.value)) {
            trackName = inputObject.sequence
        }
        if (inputObject.has(FeatureStringEnum.TRACK.value)) {
            trackName = inputObject.track
        }
        println "input object is ${inputObject as JSON} has output ${trackName}"
        return trackName
    }

    // get current user from session or input object
    User getCurrentUser(JSONObject inputObject = new JSONObject()) {
        String username = null
        if (inputObject?.has(FeatureStringEnum.USERNAME.value)) {
            username = inputObject.getString(FeatureStringEnum.USERNAME.value)
        }
        if (!username) {
            username = SecurityUtils.subject.principal
        }
        // TODO: remove
        if (false && !username) {
            return null
        }

        User user = User.findByUsername(username)

        if (!user) {
            // TODO: remove
            user = User.all.first()
        }
        return user

    }

    Organism getOrganismForTokenInDB(String token) {
        log.debug "token for org ${token}"
        if (token.isLong()) {
            log.debug "is long "
            return Organism.findById(Long.parseLong(token))
        } else {
            log.debug "is NOT long "
            // Cannot use findByCommonNameIlike, because it will fail to update the permission of an organism named orgam
            // if an organism named Orgam exist. findByCommonNameIlike ignores the case.
            return Organism.findByCommonName(token)
        }
    }

    Organism getSessionOrganism(String clientToken) {
        JSONObject preferenceObject = getSessionPreferenceObject(clientToken)
        if (preferenceObject) {
            def organismId = preferenceObject.organism.id as Long
            return Organism.get(organismId) ?: Organism.findById(organismId)
        }
        return null
    }

    Organism getOrganismForToken(String token) {
        Organism organism = getSessionOrganism(token)
        if (organism) {
            return organism
        } else {
            return getOrganismForTokenInDB(token)
        }
    }

    Organism getOrganismFromInput(JSONObject inputObject) {

        if (inputObject.has(FeatureStringEnum.ORGANISM.value)) {
            String organismString = inputObject.getString(FeatureStringEnum.ORGANISM.value)
            return getOrganismForTokenInDB(organismString)
        }
        return null
    }

    /**
     * This method finds the proper username with their proper organism for the current organism when including the track name.
     *
     * @param inputObject
     * @param requiredPermissionEnum
     * @return
     */
    Sequence checkPermissions(JSONObject inputObject, PermissionEnum requiredPermissionEnum) {
        Organism organism
        String sequenceName = getSequenceNameFromInput(inputObject)
        println "sequewnce name ? ${sequenceName}"

        User user = getCurrentUser(inputObject)
        organism = getOrganismFromInput(inputObject)
        println "organism name ? ${organism} ${organism.commonName}"
//        println "organism ${organism} from input ${inputObject as JSON}"

//        if (!organism) {
//            String clientToken = inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value)
//            UserOrganismPreferenceDTO preferenceDTO = preferenceService.getCurrentOrganismPreference(user, sequenceName, clientToken)
//            log.debug "Permission service found DTO: ${preferenceDTO as JSON}"
//            if (preferenceDTO) {
//                organism = Organism.findById(preferenceDTO.organism.id)
//            }
//        }

//        Sequence sequence
//        if (!sequenceName) {
//            sequence = UserOrganismPreference.findByClientTokenAndOrganism(sequenceName, organism, [max: 1, sort: "lastUpdated", order: "desc"])?.sequence
//        } else {
//        println "sequence name ${sequenceName} and ${organism}"
//        for (s in Sequence.all) {
//            println "sequence: ${s as JSON}"
//            println "seq name: ${s.name}"
//            println "seq org: ${s.organism}"
//            println "seq org id: ${s.organismId}"
//        }
//        Sequence sequence = Sequence.findByNameAndOrganismId(sequenceName, organism.id, [fetch: [organism: 'join']])
//        sequence = sequence ?: Sequence.(sequenceName, organism.id, [fetch: [organism: 'join']])
//        println "sequence join json ${sequenceJoin as JSON}"
//        Sequence sequence = Sequence.findByNameAndOrganismId(sequenceName, organism.id)
//        println "sequence name ${sequenceName} $sequence"
//        println "sequence json ${sequence as JSON}"
//        println "input ${sequenceName} ${organism.id}"
//        def sequences = Sequence.executeQuery("MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence) where o.commonName = ${organism.commonName} and s.name = ${sequenceName} RETURN o,r,s LIMIT 1")?.first()
        def sequences = Sequence.executeQuery("MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence) where o.commonName = ${organism.commonName} and s.name = ${sequenceName} RETURN { sequence: s} LIMIT 1").first()
        Sequence sequence = sequences.sequence as Sequence
//        println "returning sequence as cast ${sequence}"
//        println "returning sequence as JSON ${sequence as JSON}"
//        def retrievedSequences = Sequence.createCriteria().listDistinct {
//            eq("name", sequenceName)
//            eq("organismId", organism.id as Long)
////            join("organism")
//        }
//        println "A retrieved sequences ${retrievedSequences as JSON}"
//        retrievedSequences = retrievedSequences?: Sequence.createCriteria().listDistinct {
//            eq("name", sequenceName)
//                organism{
//                    eq("commonName",organism.commonName)
//                }
////            eq("organismId", organism.id as Long)
//        }
//        println "B retrieved sequences ${retrievedSequences as JSON}"
//        println "seq ${sequence} org ${organism} , sorg ${sequence.organismId}, ${sequence.organism}"
//            if (!sequence) {
//                throw new AnnotationException("No sequence found for name '${sequenceName}' and organism '${organism?.commonName}'")
//            }
//        }

//        if (!sequence && organism) {
//            sequence = Sequence.findByOrganism(organism, [max: 1, sort: "end", order: "desc"])
//        }

        List<PermissionEnum> permissionEnums = getOrganismPermissionsForUser(organism, user)
        PermissionEnum highestValue = isUserGlobalAdmin(user) ? PermissionEnum.ADMINISTRATE : findHighestEnum(permissionEnums)

        if (highestValue.rank < requiredPermissionEnum.rank) {
            log.debug "highest value ${highestValue}"
            log.debug "required permission ${requiredPermissionEnum}"
            log.debug "highest value display ${highestValue.display}"
            log.debug "permission display ${requiredPermissionEnum.display}"
            throw new AnnotationException("You have insufficient permissions [${highestValue.display} < ${requiredPermissionEnum.display}] to perform this operation")
        }
        return sequence
    }

    Boolean checkPermissions(PermissionEnum requiredPermissionEnum) {
        // TODO: remove this line
        return true
        try {
            Session session = SecurityUtils.subject.getSession(false)
            if (session) {
                Map<String, Integer> permissions = (Map<String, Integer>) session.getAttribute(FeatureStringEnum.PERMISSIONS.getValue())
                // permissions not always on session if they come through a web-service, see #1759
                if (!permissions) {
                    User user = User.findByUsername(SecurityUtils.subject.principal.toString())
                    permissions = getPermissionsForUser(user)
                }
                if (permissions) {
                    Integer permission = permissions.get(SecurityUtils.subject.principal)
                    PermissionEnum sessionPermissionsEnum = isAdmin() ? PermissionEnum.ADMINISTRATE : PermissionEnum.getValueForOldInteger(permission)

                    if (sessionPermissionsEnum == null) {
                        log.warn "No permissions found in session"
                        return false
                    }

                    if (sessionPermissionsEnum.rank < requiredPermissionEnum.rank) {
                        log.warn "Permission required ${requiredPermissionEnum.display} vs found ${sessionPermissionsEnum.display}"
                        return false
                    }
                    return true
                } else {
                    log.debug "No permissions found on session"
                }
            } else {
                log.debug "No session found"
            }

        } catch (e) {
            log.error "Error checking permissions from session ${e}"
            e.printStackTrace()
            return false
        }
        return false

    }

    PermissionEnum checkPermissions(JSONObject jsonObject, Organism organism, PermissionEnum requiredPermissionEnum) {

        //def session = RequestContextHolder.currentRequestAttributes().getSession()
        String username = jsonObject.getString(FeatureStringEnum.USERNAME.value)


        User user = User.findByUsername(username)

        List<PermissionEnum> permissionEnums = getOrganismPermissionsForUser(organism, user)
        PermissionEnum highestValue = isUserGlobalAdmin(user) ? PermissionEnum.ADMINISTRATE : findHighestEnum(permissionEnums)

        if (highestValue.rank < requiredPermissionEnum.rank) {
            //return false
            throw new AnnotationException("You have insufficient permissions [${highestValue.display} < ${requiredPermissionEnum.display}] to perform this operation")
        }

        return highestValue
    }

    /**
     * This method validates after logged in, so it *should* not need a special authenticator.
     * @param jsonObject
     * @return
     */
    JSONObject validateSessionForJsonObject(JSONObject jsonObject) {
        // not sure if permissions with translate through or not
        Session session = SecurityUtils.subject.getSession(false)
        if (!session) {
            // login with jsonObject username and password
            log.debug "creating session with found json object ${jsonObject.username}, ${jsonObject.password as String}"
            if (!jsonObject.username) {
                log.error "Username not supplied so can not authenticate."
                jsonObject.error_message = "Username not supplied so can not authenticate."
                return jsonObject
            }

            def authToken = new UsernamePasswordToken(jsonObject.username, jsonObject.password as String)

            try {
                Subject subject = SecurityUtils.getSubject()
                subject.getSession(true)
//                session = subject.getSession(true)

                subject.login(authToken)
                if (!subject.authenticated) {
                    log.warn "Failed to authenticate user ${jsonObject.username}"
                    jsonObject.error_message = "Failed to authenticate user ${jsonObject.username}"
                    return jsonObject
                }
            } catch (Exception ae) {
                log.error("Problem authenticating: " + ae.fillInStackTrace())
                jsonObject.error_message = "Problem authenticating: " + ae.fillInStackTrace()
                return jsonObject
            }
        } else if (!jsonObject.username && SecurityUtils?.subject?.principal) {
            jsonObject.username = SecurityUtils?.subject?.principal
        } else if (!jsonObject.username && session.attributeKeys.contains(FeatureStringEnum.USERNAME.value)) {
            jsonObject.username = session.getAttribute(FeatureStringEnum.USERNAME.value)
        } else if (jsonObject.password && jsonObject.username) {
            // check the authentication of the username and password passed by webservice
            def authToken = new UsernamePasswordToken(jsonObject.username, jsonObject.password as String)
            Subject subject = SecurityUtils.getSubject()
            subject.getSession(true)
            subject.login(authToken)
            if (!subject.authenticated) {
                jsonObject.error_message = "Failed to authenticate user ${jsonObject.username}"
                return jsonObject
            }
        }
        return jsonObject
    }

    Boolean hasGlobalPermissions(JSONObject jsonObject, PermissionEnum permissionEnum) {

        GlobalPermissionEnum globalPermissionEnum = mapLocalPermissionToGlobal(permissionEnum)
        return hasGlobalPermissions(jsonObject, globalPermissionEnum)
    }

    /**
     * Find the next highest global permission.
     * In this case I've set it to the next highest rank.  So a local ADMINISTRATOR should map to a GLOBAL administrator?!?
     *
     * @param permissionEnum
     * @return
     */
    GlobalPermissionEnum mapLocalPermissionToGlobal(PermissionEnum permissionEnum) {
        int rank = permissionEnum.rank

        for (gpe in GlobalPermissionEnum.values().sort() { a, b -> a.rank <=> b.rank }) {
            if (gpe.rank >= rank) {
                return gpe
            }
        }
        return null

    }
/**
 * If a user exists and is a admin (not just for organism), then check, otherwise a regular user is still a valid user.
 * @param jsonObject
 * @param permissionEnum
 * @return
 */
    Boolean hasGlobalPermissions(JSONObject jsonObject, GlobalPermissionEnum permissionEnum) {
        // TODO: remove this line
        return true
        // check the authentication
        // we need to validate the session before we check for the username
        jsonObject = validateSessionForJsonObject(jsonObject)
        if (jsonObject.username == null) {
            log.debug("User not logged in")
            return false
        }
        User user = User.findByUsername(jsonObject.username)
        if (!user) {
            log.error("User ${jsonObject.username} does not exist in the database.")
            return false
        }
        if (jsonObject.error_message) {
            log.error("Error with user permissions ${user.username}:  ${jsonObject.error_message}")
            return false
        }
        return isUserBetterOrEqualRank(user, permissionEnum)
        // if the rank required is less than administrator than ask if they are an administrator
//        if (PermissionEnum.ADMINISTRATE.rank < permissionEnum.rank) {
//            return isUserGlobalAdmin(user)
//        }
//        return true
    }

    Boolean hasPermissions(JSONObject jsonObject, PermissionEnum permissionEnum) {
        // no need to check the global permission, just need to check the organism permission
        /*
        if (!hasGlobalPermissions(jsonObject, permissionEnum)) {
            log.info("User lacks permissions ${permissionEnum.display}")
            return false
        }
        */
        String clientToken = jsonObject.getString(FeatureStringEnum.CLIENT_TOKEN.value)
        // use validateSessionForJsonObject to get the username of the current user into jsonObject, which is needed for checkPermissions
        jsonObject = validateSessionForJsonObject(jsonObject)
        Organism organism = getOrganismFromInput(jsonObject)

        organism = organism ?: getOrganismForToken(clientToken)
        // don't set the preferences if it is coming off a script
//        if (clientToken != FeatureStringEnum.IGNORE.value) {
//            preferenceService.setCurrentOrganism(getCurrentUser(), organism, clientToken)
//        }

        return checkPermissions(jsonObject, organism, permissionEnum)

    }


    Boolean hasAnyPermissions(User user) {

        Map<String, Integer> permissions = getPermissionsForUser(user)
        if (!permissions) {
            return false
        }

        for (Integer value : permissions.values()) {
            if (value > PermissionEnum.NONE.value) {
                return true
            }
        }

        return false
    }

    PermissionEnum findHighestOrganismPermissionForCurrentUser(Organism organism) {
        findHighestOrganismPermissionForUser(organism, currentUser)
    }

    PermissionEnum findHighestOrganismPermissionForUser(Organism organism, User user) {
        List<PermissionEnum> permissionEnums = getOrganismPermissionsForUser(organism, user)

        PermissionEnum highestEnum = PermissionEnum.NONE
        for (PermissionEnum permissionEnum : permissionEnums) {
            if (permissionEnum.rank > highestEnum.rank) {
                highestEnum = permissionEnum
            }
        }
        return highestEnum
    }

    Map<Organism, Boolean> userHasOrganismPermissions(PermissionEnum permissionEnum) {
        Map<Organism, Boolean> organismUserMap = [:]
        UserOrganismPermission.findAllByUser(currentUser).each { permission ->
            PermissionEnum highestPermssion = findHighestOrganismPermissionForCurrentUser(permission.organism)
            organismUserMap.put(permission.organism, highestPermssion?.rank >= permissionEnum?.rank)
        }

        return organismUserMap
    }

    Boolean userHasOrganismPermission(Organism organism, PermissionEnum permissionEnum) {
        return findHighestOrganismPermissionForCurrentUser(organism).rank >= permissionEnum.rank
    }

    def authenticateWithToken(UsernamePasswordToken usernamePasswordToken = null, HttpServletRequest request) {

        def authentications = configWrapperService.authentications

        for (auth in authentications) {
            if (auth.active) {
                def authenticationService
                if ("remoteUserAuthenticatorService" == auth.className) {
                    authenticationService = remoteUserAuthenticatorService
                    if (auth?.params?.containsKey("default_group")) {
                        authenticationService.setDefaultGroup(auth.params.get("default_group"))
                    }
                } else if ("usernamePasswordAuthenticatorService" == auth.className) {
                    authenticationService = usernamePasswordAuthenticatorService
                } else {
                    log.error("No authentication service for ${auth.className}")
                    // better to return false if mis-configured
                    return false
                }

                if (authenticationService.requiresToken()) {
                    def req = handleInput(request, request.parameterMap)
                    def authToken = usernamePasswordToken ?: null
                    if (!authToken && req.username) {
                        authToken = new UsernamePasswordToken(req.username as String, req.password as String)
                    }
                    if (authenticationService.authenticate(authToken, request)) {
                        log.info "Authenticated user ${authToken.username} using ${auth.name}"
                        return true
                    }
                } else {
                    if (authenticationService.authenticate(request)) {
                        log.info "Authenticated user ${auth.name}"
                        return true
                    }
                }
            }
        }
        log.warn "Failed to authenticate user"
        return false
    }

    /**
     * Verifies that "userId" matches userName for the secured session user
     * @param jsonObject
     * @return
     */
    Boolean sameUser(JSONObject jsonObject, HttpServletRequest request) {
        // not sure if permissions with translate through or not
        Session session = SecurityUtils.subject.getSession(false)
        if (!session) {
            // login with jsonObject tokens
            log.debug "creating session with found json object ${jsonObject.username}, ${jsonObject.password as String}"
            UsernamePasswordToken authToken = new UsernamePasswordToken(jsonObject.username, jsonObject.password as String)
            authenticateWithToken(authToken, request)
        } else if (!jsonObject.username && SecurityUtils?.subject?.principal) {
            jsonObject.username = SecurityUtils?.subject?.principal
        } else if (!jsonObject.username && session.attributeKeys.contains(FeatureStringEnum.USERNAME.value)) {
            jsonObject.username = session.getAttribute(FeatureStringEnum.USERNAME.value)
        }
        if (jsonObject.username) {
            User user = User.findByUsername(jsonObject.username)
            return user?.id == jsonObject.userId
        }
        return false
    }

    @Transactional
    def removeAllPermissions(Organism organism) {
        def userPermissions = UserOrganismPermission.findAllByOrganism(organism)
        UserOrganismPermission.deleteAll(userPermissions)
        def groupPermissions = GroupOrganismPermission.findAllByOrganism(organism)
        GroupOrganismPermission.deleteAll(groupPermissions)
    }

    @NotTransactional
    def getInsufficientPermissionMessage(PermissionEnum permissionEnum) {
        if (permissionEnum == PermissionEnum.ADMINISTRATE) {
            return "Must have permissions ${PermissionEnum.ADMINISTRATE.display}."
        } else {
            return "Must have permissions ${permissionEnum.display} or better."
        }
    }

    /**
     * we prefer the param over the dataObject one I guess
     * @param params
     * @param dataObject
     * @return
     */
    @NotTransactional
    String handleToken(GrailsParameterMap params, JSONObject dataObject) {
        // replace the dataObject either way
        if (params.containsKey(FeatureStringEnum.CLIENT_TOKEN.value)) {
            dataObject.put(FeatureStringEnum.CLIENT_TOKEN.value, params.get(FeatureStringEnum.CLIENT_TOKEN.value))
        }
        // if the dataObject doesn't contain nor does the param, then we create it
        if (!dataObject.containsKey(FeatureStringEnum.CLIENT_TOKEN.value)) {
            // client should generate token, not server
//            dataObject.put(FeatureStringEnum.CLIENT_TOKEN.value,ClientTokenGenerator.generateRandomString())
            dataObject.put(FeatureStringEnum.CLIENT_TOKEN.value, FeatureStringEnum.IGNORE.value)
        }
        String clientToken = dataObject.get(FeatureStringEnum.CLIENT_TOKEN.value)
        if (!dataObject.containsKey(FeatureStringEnum.ORGANISM.value)) {
            log.debug("dataObject does not contain organism (may not be needed)")
        }
        return clientToken
    }

    @NotTransactional
    JSONObject handleInput(HttpServletRequest request, GrailsParameterMap params) {
        JSONObject payloadJson = new JSONObject()
        if (request.JSON) {
            payloadJson = request.JSON as JSONObject
        }
        if (!payloadJson || payloadJson.size() == 0) {
            if (params.data) {
                payloadJson = JSON.parse(params.data.toString()) as JSONObject
            } else {
                payloadJson = params as JSONObject
            }
        }
        handleToken(params, payloadJson)
        return payloadJson
    }

    JSONObject handleInput(HttpServletRequest request, Map params) {
        JSONObject payloadJson = new JSONObject()
        if (request.JSON) {
            payloadJson = request.JSON as JSONObject
        } else {
            params.keySet().each { key ->
                // TODO: what about this?
                payloadJson.put(key, params.get(key)[0])

            }
        }
        return payloadJson
    }

    SequenceDTO getDTOFromSequence(Sequence sequence) {
//        if(!sequence) return null
        OrganismDTO organismDTO = getDTOFromOrganism(sequence.organism)
        SequenceDTO sequenceDTO = new SequenceDTO(
            id: sequence.id
            , organism: organismDTO
            , name: sequence.name
            , start: sequence.start
            , end: sequence.end
            , length: sequence.length
        )
        return sequenceDTO
    }

    OrganismDTO getDTOFromOrganism(Organism organism) {
        OrganismDTO organismDTO = new OrganismDTO(
            id: organism.id
            , commonName: organism.commonName
            , directory: organism.directory
        )
        return organismDTO
    }

    UserDTO getDTOFromUser(User user) {
        UserDTO userDTO = new UserDTO(
            id: user.id
            , username: user.username
        )
        return userDTO
    }


    UserOrganismPreferenceDTO getSessionPreference(String clientToken) {
        JSONObject preferenceObject = getSessionPreferenceObject(clientToken)
        return preferenceObject ? getDTOPreferenceFromObject(preferenceObject) : null
    }

    UserOrganismPreferenceDTO getDTOPreferenceFromObject(JSONObject userOrganismPreferenceObject) {
        OrganismDTO organismDTO = getDTOFromOrganismFromObject(userOrganismPreferenceObject.getJSONObject(FeatureStringEnum.ORGANISM.value))
        SequenceDTO sequenceDTO = getDTOSequenceFromObject(userOrganismPreferenceObject.getJSONObject(FeatureStringEnum.SEQUENCE.value))
        UserDTO userDTO = getDTOUserFromObject(userOrganismPreferenceObject.getJSONObject("user"))
        UserOrganismPreferenceDTO userOrganismPreferenceDTO = new UserOrganismPreferenceDTO(
            organism: organismDTO
            , sequence: sequenceDTO
            , id: userOrganismPreferenceObject.id
            , user: userDTO
            , currentOrganism: userOrganismPreferenceObject.currentOrganism
            , nativeTrackList: userOrganismPreferenceObject.nativeTrackList
            , startbp: userOrganismPreferenceObject.startbp
            , endbp: userOrganismPreferenceObject.endbp
            , clientToken: userOrganismPreferenceObject.clientToken
        )
        return userOrganismPreferenceDTO
    }

    UserDTO getDTOUserFromObject(JSONObject user) {
        UserDTO userDTO = new UserDTO(
            id: user.id
            , username: user.username
        )
        return userDTO
    }

    SequenceDTO getDTOSequenceFromObject(JSONObject sequence) {
        if (!sequence) return null
        OrganismDTO organismDTO = getDTOFromOrganismFromObject(sequence.getJSONObject(FeatureStringEnum.ORGANISM.value))
        SequenceDTO sequenceDTO = new SequenceDTO(
            id: sequence.id
            , organism: organismDTO
            , name: sequence.name
            , start: sequence.start
            , end: sequence.end
            , length: sequence.length
        )
        return sequenceDTO
    }

    OrganismDTO getDTOFromOrganismFromObject(JSONObject organism) {
        OrganismDTO organismDTO = new OrganismDTO(
            id: organism.id
            , commonName: organism.commonName
            , directory: organism.directory
        )
        return organismDTO
    }


    JSONObject getSessionPreferenceObject(String clientToken) {
        try {
            Session session = SecurityUtils.subject.getSession(false)
            if (session) {
//                printKeys(session)
                String preferenceString = session.getAttribute(FeatureStringEnum.PREFERENCE.getValue() + "::" + clientToken)?.toString()
                if (!preferenceString) return null
                return JSON.parse(preferenceString) as JSONObject
            } else {
                log.debug "No session found"
            }
        } catch (e) {
            log.debug "faild to get the gession preference objec5 ${e}"
        }
        return null
    }

    UserOrganismPreferenceDTO setSessionPreference(String clientToken, UserOrganismPreferenceDTO userOrganismPreferenceDTO) {
        Session session = SecurityUtils.subject.getSession(false)
        if (session) {
            // should be client_token , JSONObject
            String preferenceString = (userOrganismPreferenceDTO as JSON).toString()
            session.setAttribute(FeatureStringEnum.PREFERENCE.getValue() + "::" + clientToken, preferenceString)
        } else {
            log.warn "No session found"
        }
        return userOrganismPreferenceDTO
    }


    Organism getCurrentOrganismForCurrentUser(String clientToken) {
        log.debug "PS: getCurrentOrganismForCurrentUser ${clientToken}"
        Organism organism = getSessionOrganism(clientToken)
        log.debug "found organism in session ${organism} so returning"
        if (organism) return organism
        if (permissionService.currentUser == null) {
            log.warn "No user present, so using the client token"
            organism = getOrganismForTokenInDB(clientToken)
            return organism
        } else {
            UserOrganismPreferenceDTO userOrganismPreference = getCurrentOrganismPreference(permissionService.currentUser, null, clientToken)
            OrganismDTO organismDTO = setSessionPreference(clientToken, userOrganismPreference)?.organism
            return Organism.findById(organismDTO.id)
        }
    }

}
