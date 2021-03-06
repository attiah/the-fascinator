/*
 * The Fascinator - Role Manager
 * Copyright (C) 2008-2010 University of Southern Queensland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package au.edu.usq.fascinator;

import au.edu.usq.fascinator.api.PluginDescription;
import au.edu.usq.fascinator.api.PluginException;
import au.edu.usq.fascinator.api.PluginManager;
import au.edu.usq.fascinator.api.roles.Roles;
import au.edu.usq.fascinator.api.roles.RolesException;
import au.edu.usq.fascinator.api.roles.RolesManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.usq.fascinator.common.JsonConfig;

/**
 * Search and management of roles.
 *
 * This object manages one or more Roles plugins based
 * on configuration. The portal doesn't need to know
 * the details of talking to each data source.
 *
 * @author Greg Pendlebury
 */
public class RoleManager implements RolesManager{

    // The internal plugin is the default if none are specified
    private static final String INTERNAL_ROLES_PLUGIN = "internal";

    private final Logger log = LoggerFactory.getLogger(RoleManager.class);
    private Map<String, Roles> plugins;
    private Roles p;
    private String active = null;

    @Override
    public String getId() {
        return "rolemanager";
    }

    @Override
    public String getName() {
        return "Role Manager";
    }

    @Override
    public void init(String jsonString) throws RolesException {
        try {
            JsonConfig config = new JsonConfig(new ByteArrayInputStream(
                    jsonString.getBytes("UTF-8")));
            setConfig(config);
        } catch (RolesException e) {
            throw new RolesException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RolesException(e);
        } catch (IOException e) {
            throw new RolesException(e);
        }
    }

    @Override
    public void init(File jsonFile) throws RolesException {
        try {
            JsonConfig config = new JsonConfig(jsonFile);
            setConfig(config);
        } catch (RolesException e) {
            throw new RolesException(e);
        } catch (IOException ioe) {
            throw new RolesException(ioe);
        }
    }

    public void setConfig(JsonConfig config) throws RolesException {
        // Initialise our local properties
        plugins = new LinkedHashMap<String, Roles>();
        // Get and parse the config
        String plugin_string = config.get("roles/type", INTERNAL_ROLES_PLUGIN);
        String[] plugin_list = plugin_string.split(",");
        // Now start each required plugin
        for (int i = 0; i < plugin_list.length; i++) {
            // Get the plugin from the service loader
            Roles r = PluginManager.getRoles(plugin_list[i]);
            // Pass it our config file
            try {
                r.init(config.toString());
                plugins.put(plugin_list[i], r);
            } catch (NullPointerException e) {
                log.debug("Null pointer during plugin init");
            } catch (PluginException e) {
                throw new RolesException(e);
            }
        }

        // Fall back to internal if there were no other plugins
        if (active == null) active = INTERNAL_ROLES_PLUGIN;
    }

    @Override
    public void shutdown() throws RolesException {
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            try {
                p.shutdown();
            } catch (PluginException e) {
                throw new RolesException(e);
            }
        }
    }

    /**
     * Find and return all roles this user has.
     *
     * @param username The username of the user.
     * @return An array of role names (String).
     */
    @Override
    public String[] getRoles(String username) {
        List<String> found = new ArrayList();
        String[] result;

        // Loop through each plugin
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            result = p.getRoles(username);
            for (int j = 0; j < result.length; j++) {
                if (!found.contains(result[j]))
                    found.add(result[j]);
            }
        }

        return found.toArray(new String[found.size()]);
    }

    /**
     * Returns a list of users who have a particular role.
     *
     * @param role The role to search for.
     * @return An array of usernames (String) that have that role.
     */
    @Override
    public String[] getUsersInRole(String role) {
        List<String> found = new ArrayList();
        String[] result;

        // Loop through each plugin
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            result = p.getUsersInRole(role);
            for (int j = 0; j < result.length; j++) {
                if (!found.contains(result[j]))
                    found.add(result[j]);
            }
        }

        return found.toArray(new String[found.size()]);
    }

    /**
     * Method for testing if the implementing plugin allows
     * the creation, deletion and modification of roles.
     *
     * @return true/false reponse.
     */
    @Override
    public boolean supportsRoleManagement() {
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            // Return true as soon as we
            // find one plugin
            if (p.supportsRoleManagement()) {
                return true;
            }
        }
        // Return false if none
        return false;
    }

    /**
     * Assign a role to a user.
     *
     * @param username The username of the user.
     * @param newrole The new role to assign the user.
     * @throws RolesException if there was an error during assignment.
     */
    @Override
    public void setRole(String username, String newrole)
            throws RolesException {
        // Try the active plugin first
        try {
            plugins.get(active).setRole(username, newrole);
            return;
        } catch (RolesException e) {
            // We're going to try other sources now
        }

        // Now try all the others
        Boolean success = false;
        Iterator i = plugins.values().iterator();
        while (i.hasNext() && !success) {
            p = (Roles) i.next();
            try {
                // Make we don't try the active plugin again
                if (!active.equals(p.getId())) {
                    p.setRole(username, newrole);
                    success = true;
                }
            } catch (RolesException e) {
                // Do nothing, we've set a 'success' flag if
                //  any one source works.
            }
        }

        if (!success) {
            throw new RolesException("Failed to set role '" + newrole + "' for user '" + username + "'");
        }
    }

    /**
     * Remove a role from a user.
     *
     * @param username The username of the user.
     * @param oldrole The role to remove from the user.
     * @throws RolesException if there was an error during removal.
     */
    @Override
    public void removeRole(String username, String oldrole)
            throws RolesException {
        // Try the active plugin first
        try {
            plugins.get(active).removeRole(username, oldrole);
            return;
        } catch (RolesException e) {
            // We're going to try other sources now
        }

        // Now try all the others
        Boolean success = false;
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            try {
                // Make we don't try the active plugin again
                if (!active.equals(p.getId())) {
                    p.removeRole(username, oldrole);
                    success = true;
                }
            } catch (RolesException e) {
                // Do nothing, we've set a 'success' flag if
                //  any one source works.
            }
        }

        if (!success) {
            throw new RolesException("Failed to remove role '" + oldrole + "' for user '" + username + "'");
        }
    }

    /**
     * Create a role.
     *
     * @param rolename The name of the new role.
     * @throws RolesException if there was an error creating the role.
     */
    @Override
    public void createRole(String rolename)
            throws RolesException {
        try {
            plugins.get(active).createRole(rolename);
            return;
        } catch (RolesException e) {
            throw new RolesException(e);
        }
    }

    /**
     * Delete a role.
     *
     * @param rolename The name of the role to delete.
     * @throws RolesException if there was an error during deletion.
     */
    @Override
    public void deleteRole(String rolename) throws RolesException {
        // Try the active plugin first
        try {
            plugins.get(active).deleteRole(rolename);
            return;
        } catch (RolesException e) {
            throw new RolesException(e);
        }
    }

    /**
     * Rename a role.
     *
     * @param oldrole The name role currently has.
     * @param newrole The name role is changing to.
     * @throws RolesException if there was an error during rename.
     */
    @Override
    public void renameRole(String oldrole, String newrole)
            throws RolesException {
        // Try the active plugin first
        try {
            plugins.get(active).renameRole(oldrole, newrole);
            return;
        } catch (RolesException e) {
            // We're going to try other sources now
        }

        // Now try all the others
        Boolean success = false;
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            try {
                // Make we don't try the active plugin again
                if (!active.equals(p.getId())) {
                    p.renameRole(oldrole, newrole);
                    success = true;
                }
            } catch (RolesException e) {
                // Do nothing, we've set a 'success' flag if
                //  any one source works.
            }
        }

        if (!success) {
            throw new RolesException("Failed to rename role '" + oldrole + "'");
        }
    }

    /**
     * Returns a list of roles matching the search.
     *
     * @param search The search string to execute.
     * @return An array of role names that match the search.
     * @throws RolesException if there was an error searching.
     */
    @Override
    public String[] searchRoles(String search) throws RolesException {
        List<String> found = new ArrayList();
        String[] result;

        // Try the active plugin first
        if (active != null) {
            result = plugins.get(active).searchRoles(search);
            for (int i = 0; i < result.length; i++) {
                if (!found.contains(result[i]))
                    found.add(result[i]);
            }
            return found.toArray(new String[found.size()]);
        }

        // Loop through each plugin
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            result = p.searchRoles(search);
            for (int j = 0; j < result.length; j++) {
                if (!found.contains(result[j]))
                    found.add(result[j]);
            }
        }

        return found.toArray(new String[found.size()]);
    }

    /**
     * Specifies which plugin the authentication manager should use
     * when managing users. This won't effect reading of data, just
     * writing.
     *
     * @param pluginId The id of the plugin.
     */
    @Override
    public void setActivePlugin(String pluginId) {
        // Make sure it exists
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            if (pluginId.equals(p.getId())) {
                active = pluginId;
            }
        }
    }

    /**
     * Return the current active plugin.
     *
     * @return The currently active plugin.
     */
    @Override
    public String getActivePlugin() {
        return active;
    }

    /**
     * Return the list of plugins being managed.
     *
     * @return A list of plugins.
     */
    @Override
    public List<PluginDescription> getPluginList() {
        List<PluginDescription> found = new ArrayList();
        PluginDescription result;

        // Loop through each plugin
        Iterator i = plugins.values().iterator();
        while (i.hasNext()) {
            p = (Roles) i.next();
            result = new PluginDescription(p);
            found.add(result);
        }

        return found;
    }
}
