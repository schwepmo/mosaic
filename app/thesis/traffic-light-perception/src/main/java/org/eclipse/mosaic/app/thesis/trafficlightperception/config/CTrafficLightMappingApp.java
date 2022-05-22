/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.thesis.trafficlightperception.config;

import com.google.gson.annotations.JsonAdapter;
import org.eclipse.mosaic.lib.util.gson.TimeFieldAdapter;
import org.eclipse.mosaic.lib.util.gson.UnitFieldAdapter;
import org.eclipse.mosaic.rti.TIME;

public class CTrafficLightMappingApp {

    public String trafficLightMappingServerName = "server_0";
    /**
     * Perception will be triggered in this interval. [ns]
     */
    @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
    public long perceptionInterval = 1 * TIME.SECOND;
    /**
     * Viewing angle for perception module. [degree]
     */
    public double viewingAngle = 75d;
    /**
     * Viewing range for perception module. [m]
     */
    @JsonAdapter(UnitFieldAdapter.DistanceMeters.class)
    public double viewingRange = 100d;
    /**
     * Distance between vehicle and TL (1m on default). [m]
     */
    @JsonAdapter(UnitFieldAdapter.DistanceMeters.class)
    public double distanceToTrafficLight = 1;
}
