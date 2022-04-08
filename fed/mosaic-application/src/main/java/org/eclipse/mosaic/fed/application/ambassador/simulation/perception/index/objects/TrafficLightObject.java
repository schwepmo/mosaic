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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;

public class TrafficLightObject extends Vector3d implements SpatialObject {

    private final String id;
    private final String trafficLightGroupId;
    private final MutableCartesianPoint cartesianPosition = new MutableCartesianPoint();

    private TrafficLightState trafficLightState;

    public TrafficLightObject(String id, String trafficLightGroupId) {
        this.id = id;
        this.trafficLightGroupId = trafficLightGroupId;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public CartesianPoint getProjectedPosition() {
        return null;
    }
}
