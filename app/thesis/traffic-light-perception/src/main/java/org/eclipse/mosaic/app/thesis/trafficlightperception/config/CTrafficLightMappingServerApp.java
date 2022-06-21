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

import org.eclipse.mosaic.rti.TIME;

import java.io.File;

public class CTrafficLightMappingServerApp {
    /**
     * Name of the json-file to store the traffic light mappings in.
     */
    public String trafficLightMappingFileName = "mapped_traffic_lights.json";
    /**
     * If {@code true} traffic light positions will be stored in a json-file and be read in the beginning
     * of the simulation.
     */
    public boolean persistentTrafficLightMapping = true;


    // FOR EVALUATION
    /**
     * write statistics of mapped tls
     */
    public boolean writeCsv = false;
    public String csvDirectory;
    /**
     * will be used in file name of csv
     */
    public double penetrationRate = 0.01;
    /**
     * will be used in file name of csv
     */
    public long perceptionInterval = 1 * TIME.SECOND;
}
