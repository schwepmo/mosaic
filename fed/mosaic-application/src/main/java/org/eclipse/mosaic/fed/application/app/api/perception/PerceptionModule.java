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

package org.eclipse.mosaic.fed.application.app.api.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;

import java.util.List;

public interface PerceptionModule<ConfigT extends PerceptionModuleConfiguration> {

    /**
     * Returns The configuration of the {@link PerceptionModule}.
     */
    PerceptionModuleConfiguration getConfiguration();

    /**
     * Enables and configures this perception module.
     *
     * @param configuration the configuration object
     */
    void enable(ConfigT configuration);

    /**
     * Call to get all vehicles within perception range.
     *
     * @return a list of all {@link VehicleObject}s inside the perception range of this vehicle.
     * Returns {@code true} if {@link PerceptionModule} is enabled, otherwise {@code false}.
     */
    boolean isEnabled();

    /**
     * Returns a list of all {@link VehicleObject}s inside the perception range of this vehicle.
     */
    List<VehicleObject> getPerceivedVehicles();
    /**
     * Call to get all traffic lights within perception range.
     *
     * @return a list of all {@link TrafficLightObject}s inside the perception range of this vehicle.
     */
    List<TrafficLightObject> getPerceivedTrafficLights();
}
