/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.action.import_;

import io.crate.client.action.import_.ImportRequestBuilder;
import org.elasticsearch.action.Action;
import org.elasticsearch.client.Client;

public class ImportAction extends Action<ImportRequest, ImportResponse, ImportRequestBuilder> {

    public static final ImportAction INSTANCE = new ImportAction();
    public static final String NAME = "el-crate-import";

    private ImportAction() {
        super(NAME);
    }

    @Override
    public ImportResponse newResponse() {
        return new ImportResponse();
    }

    @Override
    public ImportRequestBuilder newRequestBuilder(Client client) {
        return new ImportRequestBuilder(client);
    }
}