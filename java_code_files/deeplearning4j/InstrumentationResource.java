/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.instrumentation.server;

import org.nd4j.linalg.api.instrumentation.LogEntry;
import org.nd4j.linalg.factory.Nd4j;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;

/**
 * Instrumentation resource
 *
 * @author Adam Gibson
 */
@Path("/instrumentation")
@Produces(MediaType.APPLICATION_JSON)
public class InstrumentationResource {

    @GET
    @Path("/numalive")
    public Response getNumAlive() {
        Collection<LogEntry> alive = Nd4j.getInstrumentation().getStillAlive();
        return Response.ok(alive.size()).build();
    }

    @GET
    @Path("/numdead")
    public Response getNumDead() {
        Collection<LogEntry> alive = Nd4j.getInstrumentation().getDestroyed();
        return Response.ok(alive.size()).build();
    }

    @GET
    @Path("/alive")
    public Response getAlive() {
        Collection<LogEntry> alive = Nd4j.getInstrumentation().getStillAlive();
        return Response.ok(alive).build();
    }

    @GET
    @Path("/statusof")
    public Response isAlive(@QueryParam("id") String id) {
        Boolean alive = Nd4j.getInstrumentation().isDestroyed(id);
        return Response.ok(alive).build();
    }



    @GET
    @Path("/dead")
    public Response getDead() {
        Collection<LogEntry> dead = Nd4j.getInstrumentation().getDestroyed();
        return Response.ok(dead).build();
    }
}
