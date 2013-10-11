package org.cratedb.module.sql.benchmark;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.cratedb.action.sql.SQLAction;
import org.cratedb.action.sql.SQLRequest;
import org.cratedb.action.sql.SQLResponse;
import org.elasticsearch.action.delete.DeleteAction;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryAction;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark-delete")
public class DeleteBenchmark extends BenchmarkBase {

    @Rule
    public TestRule benchmarkRun = RuleChain.outerRule(new BenchmarkRule()).around(super.ruleChain);

    public static final int NUM_REQUESTS_PER_TEST = 10;
    public static final int BENCHMARK_ROUNDS = 24; // Don't exceed the number of deletable rows
    static {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
    }

    private List<String> ids = new ArrayList<>(250);
    private List<String> countryCodes = new ArrayList<>(250);

    @Override
    public boolean loadData() {
        return true;
    }

    @Before
    public void prepare() {
        if (ids.isEmpty() || countryCodes.isEmpty()) {
            // setupOnce non-static
            SQLRequest request = new SQLRequest("SELECT \"_id\", \"countryCode\" FROM countries");
            SQLResponse response = client().execute(SQLAction.INSTANCE, request).actionGet();
            for (int i=0; i<response.rows().length;i++ ) {
                ids.add((String) response.rows()[i][0]);
                countryCodes.add((String) response.rows()[i][1]);
            }
        }
    }

    @After
    public void dropIndex() {
        // Drop the index so it will be refilled for the next test-round
        // refill will only affect warmup-round, not benchmark itself
        if (ids.isEmpty() || countryCodes.isEmpty()) {
            wipeIndices(client(), INDEX_NAME);
        }
    }

    public String getDeleteId() {
        if (ids.isEmpty()) {
            prepare();
        }
        return ids.remove(0);
    }

    public String getCountryCode() {
        if (countryCodes.isEmpty()) {
            prepare();
        }
        return countryCodes.remove(0);
    }

    public DeleteRequest getDeleteApiByIdRequest() {
        return new DeleteRequest(INDEX_NAME, "default", getDeleteId());
    }

    public SQLRequest getDeleteSqlByIdRequest() {
        return new SQLRequest("DELETE FROM countries WHERE \"_id\"=?", new Object[]{ getDeleteId() });
    }

    public SQLRequest getDeleteSqlByQueryRequest() {
        return new SQLRequest("DELETE FROM countries WHERE \"countryCode\"=?", new Object[]{ getCountryCode() });
    }

    public DeleteByQueryRequest getDeleteApiByQueryRequest() throws IOException {

        return new DeleteByQueryRequest(INDEX_NAME).query(
                XContentFactory.jsonBuilder()
                        .startObject()
                        .startObject("term")
                        .field("countryCode", getCountryCode())
                        .endObject()
                        .endObject().bytes().toBytes()
        );
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = 1)
    @Test
    public void testDeleteApiById() {
        for (int i=0; i<NUM_REQUESTS_PER_TEST; i++) {
            DeleteResponse response = client().execute(DeleteAction.INSTANCE, getDeleteApiByIdRequest()).actionGet();
            assertFalse(response.isNotFound());
        }
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = 1)
    @Test
    public void testDeleteApiByQuery() throws IOException {
        for (int i=0; i<NUM_REQUESTS_PER_TEST; i++) {
            client().execute(DeleteByQueryAction.INSTANCE, getDeleteApiByQueryRequest()).actionGet();
        }
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = 1)
    @Test
    public void testDeleteSqlById() {
        for (int i=0; i<NUM_REQUESTS_PER_TEST; i++) {
            SQLResponse response = client().execute(SQLAction.INSTANCE, getDeleteSqlByIdRequest()).actionGet();
        }
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = 1)
    @Test
    public void testDeleteSQLByQuery() {
        for (int i=0; i<NUM_REQUESTS_PER_TEST; i++) {
            SQLResponse response = client().execute(SQLAction.INSTANCE, getDeleteSqlByQueryRequest()).actionGet();
        }
    }
}