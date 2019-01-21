/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.action.privilege;

import org.elasticsearch.action.Action;

/**
 * Action for deleting application privileges.
 */
public final class DeletePrivilegesAction extends Action<DeletePrivilegesResponse> {

    public static final DeletePrivilegesAction INSTANCE = new DeletePrivilegesAction();
    public static final String NAME = "cluster:admin/xpack/security/privilege/delete";

    private DeletePrivilegesAction() {
        super(NAME);
    }

    @Override
    public DeletePrivilegesResponse newResponse() {
        return new DeletePrivilegesResponse();
    }
}
