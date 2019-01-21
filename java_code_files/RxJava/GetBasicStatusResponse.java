/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.license;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;

public class GetBasicStatusResponse extends ActionResponse implements ToXContentObject {

    private boolean eligibleToStartBasic;

    GetBasicStatusResponse() {
    }

    public GetBasicStatusResponse(boolean eligibleToStartBasic) {
        this.eligibleToStartBasic = eligibleToStartBasic;
    }

    boolean isEligibleToStartBasic() {
        return eligibleToStartBasic;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        eligibleToStartBasic = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(eligibleToStartBasic);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("eligible_to_start_basic", eligibleToStartBasic);
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GetBasicStatusResponse that = (GetBasicStatusResponse) o;
        return eligibleToStartBasic == that.eligibleToStartBasic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eligibleToStartBasic);
    }
}
