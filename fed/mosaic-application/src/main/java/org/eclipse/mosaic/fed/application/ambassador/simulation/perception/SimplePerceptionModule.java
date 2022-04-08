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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import static java.lang.Math.toRadians;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.SpatialObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.spatial.BoundingBox;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import java.util.List;

/**
 * A simplified perception module which detects all vehicles within the defined field of view.
 * No occlusion or error model is considered. The field of view is defined with an opening angle of maximum 180 degrees.
 */
public class SimplePerceptionModule implements PerceptionModule<SimplePerceptionConfiguration> {
    private static final double DEFAULT_VIEWING_ANGLE = 40;
    private static final double DEFAULT_VIEWING_RANGE = 200;


    private final PerceptionModuleOwner owner;
    private final Logger log;

    private SimplePerception perceptionModel;

    public SimplePerceptionModule(PerceptionModuleOwner owner, Logger log) {
        this.owner = owner;
        this.log = log;
    }

    @Override
    public void enable(SimplePerceptionConfiguration configuration) {
        if (configuration == null) {
            log.warn("Provided perception configuration is null. Using default configuration with viewingAngle={}°, viewingRange={}m.",
                    DEFAULT_VIEWING_ANGLE, DEFAULT_VIEWING_RANGE);
            configuration = new SimplePerceptionConfiguration(DEFAULT_VIEWING_ANGLE, DEFAULT_VIEWING_ANGLE);
        }
        this.perceptionModel = new SimplePerception(this.owner.getId(), configuration);
    }

    @Override
    public List<VehicleObject> getPerceivedVehicles() {
        if (perceptionModel == null || owner.getVehicleData() == null) {
            log.warn("No perception model initialized.");
            return Lists.newArrayList();
        }
        perceptionModel.updateOrigin(owner.getVehicleData().getProjectedPosition(), owner.getVehicleData().getHeading());
        // note, the perception index is updated internally only if vehicles have moved since the last call
        SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent().updateSpatialIndices();
        // request all vehicles within the area of the field of view
        return SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent()
                .getSpatialIndex()
                .getVehiclesInRange(perceptionModel);
    }

    @Override
    public List<TrafficLightObject> getPerceivedTrafficLights() {
        if (perceptionModel == null || owner.getVehicleData() == null) {
            log.warn("No perception model initialized.");
            return Lists.newArrayList();
        }
        perceptionModel.updateOrigin(owner.getVehicleData().getProjectedPosition(), owner.getVehicleData().getHeading());
        // note, the perception index is updated internally only if vehicles have moved since the last call
        SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent().updateSpatialIndices();
        // request all vehicles within the area of the field of view
        return SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent()
                .getSpatialIndex()
                .getTrafficLightsInRange(perceptionModel);
    }


    /**
     * Checks whether the pre-selection of vehicles actually fall in the viewing range of the
     * ego vehicle. Note: We use ego-vehicle position as origin.
     */
    private static class SimplePerception implements PerceptionRange {

        private final String ownerId;
        private final SimplePerceptionConfiguration configuration;

        private final Vector3d origin = new Vector3d();
        private final Vector3d rightBoundVector = new Vector3d();
        private final Vector3d leftBoundVector = new Vector3d();

        /**
         * The axis-aligned bounding box around the sight area.
         */
        private final BoundingBox sightAreaBoundingBox = new BoundingBox();

        /**
         * Vector object used for temporary calculations, to avoid unnecessary object allocations.
         */
        private final Vector3d tmpVector1 = new Vector3d();

        /**
         * Vector object used for temporary calculations, to avoid unnecessary object allocations.
         */
        private final Vector3d tmpVector2 = new Vector3d();

        SimplePerception(String ownerId, SimplePerceptionConfiguration configuration) {
            Validate.isTrue(configuration.getViewingAngle() < 180, "Only viewing angles less than 180 degrees are supported.");

            this.ownerId = ownerId;
            this.configuration = configuration;
        }

        private void updateOrigin(CartesianPoint origin, double heading) {
            origin.toVector3d(this.origin);
            calculateSightBoundingVectors(heading);
            calculateMinimumBoundingRectangle(heading);
        }

        @Override
        public BoundingBox getBoundingBox() {
            return sightAreaBoundingBox;
        }

        @Override
        public boolean isInRange(SpatialObject other) {
            if (other.getId().equals(this.ownerId)) { // cannot see itself
                return false;
            }
            synchronized (tmpVector1) {
                // writes position of other to tmpVector1 and subtract origin to create a relative vector pointing to the other object
                other.getProjectedPosition().toVector3d(tmpVector1).subtract(origin);
                // we use tmpVector2 as origin from the viewpoint of this object
                tmpVector2.set(0, 0, 0);
                return VectorUtils.isLeftOfLine(tmpVector1, tmpVector2, rightBoundVector) // vehicle is left of right edge
                        && !VectorUtils.isLeftOfLine(tmpVector1, tmpVector2, leftBoundVector) // vehicle is right of left edge
                        && tmpVector1.magnitude() <= configuration.getViewingRange(); // other vehicle is in range
            }
        }

        /**
         * Calculates the two unit vectors circumscribing the circular sector of the viewing field.
         */
        private void calculateSightBoundingVectors(double heading) {
            synchronized (tmpVector1) {
                // getting the direction vector of the heading from origin (result is written into tmpVector1)
                Vector3d directionVector = VectorUtils.getDirectionVectorFromHeading(heading, tmpVector1);
                double viewingAngleRad = toRadians(configuration.getViewingAngle());

                directionVector.multiply(configuration.getViewingRange());

                // rotate the direction vector to the right
                rightBoundVector.set(directionVector).rotate(-viewingAngleRad / 2, VectorUtils.UP);
                // rotate the direction vector to the left
                leftBoundVector.set(directionVector).rotate(viewingAngleRad / 2, VectorUtils.UP);
            }
        }

        private void calculateMinimumBoundingRectangle(double heading) {
            synchronized (tmpVector1) {
                double headingRad = toRadians(heading);
                double halfViewingAngleRad = toRadians(configuration.getViewingAngle()) / 2;
                sightAreaBoundingBox.clear();
                // add origin, end of leftbound and end of rightbound vector as possible extremes of the bounding rectangle
                sightAreaBoundingBox.add(origin, tmpVector1.set(origin).add(leftBoundVector), tmpVector2.set(origin).add(rightBoundVector));

                // check if the opening angle includes any of the following extremes in both directions on x- and z-axis
                if (Math.abs(MathUtils.angleDif(headingRad, 0)) < halfViewingAngleRad) { // NORTH
                    sightAreaBoundingBox.add(tmpVector1.set(origin.x, origin.y, origin.z - configuration.getViewingRange()));
                }
                if (Math.abs(MathUtils.angleDif(headingRad, Math.PI / 2)) < halfViewingAngleRad) { // EAST
                    sightAreaBoundingBox.add(tmpVector1.set(origin.x + configuration.getViewingRange(), origin.y, origin.z));
                }
                if (Math.abs(MathUtils.angleDif(headingRad, Math.PI)) < halfViewingAngleRad) { // SOUTH
                    sightAreaBoundingBox.add(tmpVector1.set(origin.x, origin.y, origin.z + configuration.getViewingRange()));
                }
                if (Math.abs(MathUtils.angleDif(headingRad, (3 * Math.PI) / 2.0)) < halfViewingAngleRad) { // WEST
                    sightAreaBoundingBox.add(tmpVector1.set(origin.x - configuration.getViewingRange(), origin.y, origin.z));
                }
            }
        }
    }
}
