/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.analyze.relations;

import io.crate.analyze.InsertFromSubQueryAnalyzedStatement;
import io.crate.analyze.SelectAnalyzedStatement;
import io.crate.analyze.UpdateAnalyzedStatement;

import javax.annotation.Nullable;

public abstract class RelationVisitor<C, R> {

    public R process(AnalyzedRelation relation, @Nullable C context) {
        return relation.accept(this, context);
    }

    protected R visitAnalyzedRelation(AnalyzedRelation relation, C context) {
        throw new UnsupportedOperationException(String.format("relation \"%s\" is not supported", relation));
    }

    public R visitSelectAnalyzedStatement(SelectAnalyzedStatement selectAnalyzedStatement, C context) {
        return visitAnalyzedRelation(selectAnalyzedStatement, context);
    }

    public R visitTableRelation(TableRelation tableRelation, C context) {
        return visitAnalyzedRelation(tableRelation, context);
    }

    public R visitPlanedAnalyzedRelation(PlannedAnalyzedRelation plannedAnalyzedRelation, C context) {
        return visitAnalyzedRelation(plannedAnalyzedRelation, context);
    }

    public R visitInsertFromQuery(InsertFromSubQueryAnalyzedStatement insertFromSubQueryAnalyzedStatement, C context) {
        return visitAnalyzedRelation(insertFromSubQueryAnalyzedStatement, context);
    }

    public R visitUpdateAnalyzedStatement(UpdateAnalyzedStatement updateAnalyzedStatement, C context) {
        return visitAnalyzedRelation(updateAnalyzedStatement, context);
    }
}
