/*
 * The Fascinator - Portal
 * Copyright (C) 2010 University of Southern Queensland
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
package au.edu.usq.fascinator.portal.services.impl;

import au.edu.usq.fascinator.MessagingServices;
import au.edu.usq.fascinator.portal.HouseKeeper;
import au.edu.usq.fascinator.common.JsonConfig;
import au.edu.usq.fascinator.common.JsonConfigHelper;
import au.edu.usq.fascinator.portal.services.HouseKeepingManager;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the House Keeping Manager interface, providing access to
 * the housekeeping message queue and its outputs.
 *
 * @author Greg Pendlebury
 */
public class HouseKeepingManagerImpl implements HouseKeepingManager {

    /** Logging */
    private Logger log = LoggerFactory.getLogger(HouseKeepingManagerImpl.class);

    /** System Configuration */
    private JsonConfigHelper sysConfig;

    /** House Keeper object */
    private HouseKeeper houseKeeper;

    /** Messaging service instance */
    private MessagingServices services;

    /**
     * Basic constructor, run by Tapestry through injection.
     *
     */
    public HouseKeepingManagerImpl() {
        try {
            services = MessagingServices.getInstance();
            sysConfig = new JsonConfigHelper(JsonConfig.getSystemFile());
            List<JsonConfigHelper> configList =
                    sysConfig.getJsonList("portal/houseKeeping");
            if (configList.size() != 1) {
                log.error("Invalid config for house keeping!");
            } else {
                // Create
                houseKeeper = new HouseKeeper();
                // Initialise
                houseKeeper.init(configList.get(0));
                houseKeeper.start();
            }
        } catch (IOException ex) {
            log.error("Failed to access system config", ex);
        } catch (Exception ex) {
            log.error("Failed to start House Keeping", ex);
            houseKeeper = null;
        }
    }

    /**
     * Tapestry notification that server is shutting down
     *
     */
    @Override
    public void registryDidShutdown() {
        try {
            if (houseKeeper != null) {
                houseKeeper.stop();
            }
            services.release();
        } catch (Exception ex) {
            log.error("Error shutting down!", ex);
        }
    }

    /**
     * Are there any House Keeping actions requiring attention from the user.
     *
     * @returns boolean flag
     */
    @Override
    public boolean requiresAction() {
        if (houseKeeper == null) {
            return true;
        } else {
            return houseKeeper.requiresUserAction();
        }
    }

    /**
     * Get the message House Keeping wants to display to the user.
     *
     * @returns String containing the message to display.
     */
    @Override
    public String getMessage() {
        if (houseKeeper == null) {
            return "WARNING!: The HouseKeeping object failed to start. Please notify an administrator";
        } else {
            return houseKeeper.getUserMessage();
        }
    }

    /**
     * Get the template House Keeping wants to frame its message in.
     *
     * @returns String containing the template to use.
     */
    @Override
    public String getTemplate() {
        if (houseKeeper != null) {
            String template = houseKeeper.getDisplayTemplate();
            if (template != null) {
                return template;
            }
        }
        return null;
    }

    /**
     * Send a confirmation of the message receipt to HouseKeeping.
     *
     */
    @Override
    public void confirmMessage() {
        houseKeeper.confirmMessage();
    }

    /**
     * Send a message to HouseKeeping.
     *
     */
    @Override
    @SuppressWarnings("static-access")
    public void sendMessage(String message) {
        services.queueMessage(houseKeeper.QUEUE_ID, message);
    }

    /**
     * Request a low priority restart from HouseKeeping.
     *
     */
    @Override
    public void requestRestart() {
        log.info("System restart has been requested");
        JsonConfigHelper msg = new JsonConfigHelper();
        msg.set("type", "basic-restart");
        sendMessage(msg.toString());
    }

    /**
     * Request a high priority restart from HouseKeeping.
     * High priority will stop all user actions until the restart
     * occurs.
     *
     */
    @Override
    public void requestUrgentRestart() {
        log.info("Urgent system restart has been requested");
        JsonConfigHelper msg = new JsonConfigHelper();
        msg.set("type", "blocking-restart");
        sendMessage(msg.toString());
    }
}
