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

package org.eclipse.mosaic.app.thesis.trafficlightperception.payloads;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.ToDataOutput;
import org.eclipse.mosaic.lib.util.SerializationUtils;

import java.io.DataOutput;
import java.io.IOException;

public class TrafficLightMapping implements ToDataOutput {
    /**
     * Name of the TL to be mapped.
     */
    private final String trafficLightName;
    /**
     * Position of the TL to be mapped.
     */
    private final GeoPoint trafficLightPosition;

    public TrafficLightMapping(String trafficLightName, GeoPoint trafficLightPosition) {
        this.trafficLightName = trafficLightName;
        this.trafficLightPosition = trafficLightPosition;
    }

    public String getTrafficLightName() {
        return trafficLightName;
    }

    public GeoPoint getTrafficLightPosition() {
        return trafficLightPosition;
    }

    @Override
    public void toDataOutput(DataOutput dataOutput) throws IOException {
        dataOutput.writeChars(trafficLightName);
        SerializationUtils.encodeGeoPoint(dataOutput, trafficLightPosition);
    }
}
