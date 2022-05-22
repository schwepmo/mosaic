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

package org.eclipse.mosaic.app.thesis.trafficlightperception.messages;

import org.eclipse.mosaic.app.thesis.trafficlightperception.payloads.TrafficLightMapping;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.rti.DATA;

import javax.annotation.Nonnull;

public class TrafficLightMappingMessage extends V2xMessage {

    private static final long serialVersionUID = 1L;

    /**
     * The info for the TL to be mapped.
     */
    private final TrafficLightMapping trafficLightMapping;

    /**
     * {@link #trafficLightMapping} encoded.
     */
    private final EncodedPayload payload;

    public TrafficLightMappingMessage(MessageRouting routing, TrafficLightMapping trafficLightMapping) {
        super(routing);
        this.trafficLightMapping = trafficLightMapping;
        payload = new EncodedPayload(trafficLightMapping, 10 * DATA.BYTE);
    }

    public TrafficLightMapping getTrafficLightMapping() {
        return trafficLightMapping;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayLoad() {
        return payload;
    }
}
