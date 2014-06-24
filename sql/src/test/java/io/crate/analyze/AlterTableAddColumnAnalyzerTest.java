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

package io.crate.analyze;

import io.crate.core.collections.StringObjectMaps;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.MetaDataModule;
import io.crate.metadata.doc.DocSchemaInfo;
import io.crate.metadata.sys.MetaDataSysModule;
import io.crate.metadata.table.SchemaInfo;
import io.crate.sql.parser.ParsingException;
import org.elasticsearch.common.inject.Module;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlterTableAddColumnAnalyzerTest extends BaseAnalyzerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    static class TestMetaDataModule extends MetaDataModule {
        @Override
        protected void bindSchemas() {
            super.bindSchemas();
            SchemaInfo schemaInfo = mock(SchemaInfo.class);
            when(schemaInfo.getTableInfo(TEST_DOC_TABLE_IDENT.name())).thenReturn(userTableInfo);
            when(schemaInfo.getTableInfo(TEST_DOC_TABLE_IDENT_CLUSTERED_BY_ONLY.name()))
                    .thenReturn(userTableInfoClusteredByOnly);
            schemaBinder.addBinding(DocSchemaInfo.NAME).toInstance(schemaInfo);
        }
    }

    @Override
    protected List<Module> getModules() {
        List<Module> modules = super.getModules();
        modules.add(new TestModule());
        modules.add(new TestMetaDataModule());
        modules.add(new MetaDataSysModule());
        return modules;
    }

    @Test
    public void testAddColumnOnSystemTableIsNotAllowed() throws Exception {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("tables of schema \"sys\" are read only.");
        analyze("alter table sys.shards add column foobar string");
    }

    @Test
    public void testAddColumnWithAnalyzerAndNonStringType() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                "Can't use an Analyzer on column \"foobar['age']\" because analyzers are only allowed on columns of type \"string\"");
        analyze("alter table users add column foobar object as (age int index using fulltext)");
    }

    @Test
    public void testAddFulltextIndex() throws Exception {
        expectedException.expect(ParsingException.class);
        analyze("alter table users add column index ft_foo using fulltext (name)");
    }

    @Test
    public void testAddColumnThatExistsAlready() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The table \"users\" already has a column named \"name\"");
        analyze("alter table users add column name string");
    }

    @Test
    public void testAddColumnToATableWithoutPrimaryKey() throws Exception {
        AddColumnAnalysis analysis = (AddColumnAnalysis) analyze(
                "alter table users_clustered_by_only add column foobar string");
        Map<String, Object> mapping = analysis.analyzedTableElements().toMapping();

        Object primaryKeys = ((Map) mapping.get("_meta")).get("primary_keys");
        assertNull(primaryKeys); // _id shouldn't be included
    }

    @Test
    public void testAddColumnAsPrimaryKey() throws Exception {
        AddColumnAnalysis analysis = (AddColumnAnalysis) analyze(
                "alter table users add column additional_pk string primary key");

        assertThat(analysis.analyzedTableElements().primaryKeys(), Matchers.contains(
                "additional_pk", "id"
        ));

        AnalyzedColumnDefinition idColumn = null;
        AnalyzedColumnDefinition additionalPkColumn = null;
        for (AnalyzedColumnDefinition column : analysis.analyzedTableElements().columns()) {
            if (column.name().equals("id")) {
                idColumn = column;
            } else {
                additionalPkColumn = column;
            }
        }
        assertNotNull(idColumn);
        assertThat(idColumn.ident(), equalTo(new ColumnIdent("id")));
        assertThat(idColumn.dataType(), equalTo("long"));

        assertNotNull(additionalPkColumn);
        assertThat(additionalPkColumn.ident(), equalTo(new ColumnIdent("additional_pk")));
        assertThat(additionalPkColumn.dataType(), equalTo("string"));
    }

    @Test
    public void testAddPrimaryKeyColumnWithArrayTypeUnsupported() throws Exception {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Cannot use columns of type \"array\" as primary key");
        analyze("alter table users add column newpk array(string) primary key");
    }

    @Test
    public void testAddArrayColumn() throws Exception {
        AddColumnAnalysis analysis = (AddColumnAnalysis) analyze("alter table users add newtags array(string)");
        AnalyzedColumnDefinition columnDefinition = analysis.analyzedTableElements().columns().get(0);
        assertThat(columnDefinition.name(), Matchers.equalTo("newtags"));
        assertThat(columnDefinition.dataType(), Matchers.equalTo("string"));
        assertTrue(columnDefinition.isArrayOrInArray());
    }

    @Test
    public void testAddNewNestedObjectColumn() throws Exception {
        AddColumnAnalysis analysis = (AddColumnAnalysis) analyze(
                "alter table users add column foo['x']['y'] string");

        assertThat(analysis.analyzedTableElements().columns().size(), is(2)); // id pk column is also added
        AnalyzedColumnDefinition column = analysis.analyzedTableElements().columns().get(0);
        assertThat(column.ident(), Matchers.equalTo(new ColumnIdent("foo")));
        assertThat(column.children().size(), is(1));
        AnalyzedColumnDefinition xColumn = column.children().get(0);
        assertThat(xColumn.ident(), Matchers.equalTo(new ColumnIdent("foo", Arrays.asList("x"))));
        assertThat(xColumn.children().size(), is(1));
        AnalyzedColumnDefinition yColumn = xColumn.children().get(0);
        assertThat(yColumn.ident(), Matchers.equalTo(new ColumnIdent("foo", Arrays.asList("x", "y"))));
        assertThat(yColumn.children().size(), is(0));

        Map<String, Object> mapping = analysis.analyzedTableElements().toMapping();
        Map foo = (Map) StringObjectMaps.getByPath(mapping, "properties.foo");
        assertThat((String)foo.get("type"), is("object"));

        Map x = (Map) StringObjectMaps.getByPath(mapping, "properties.foo.properties.x");
        assertThat((String)x.get("type"), is("object"));

        Map y = (Map) StringObjectMaps.getByPath(mapping, "properties.foo.properties.x.properties.y");
        assertThat((String)y.get("type"), is("string"));
    }
}