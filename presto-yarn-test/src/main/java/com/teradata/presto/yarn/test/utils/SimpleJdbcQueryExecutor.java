/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.presto.yarn.test.utils;

import com.teradata.tempto.query.QueryExecutionException;
import com.teradata.tempto.query.QueryExecutor;
import com.teradata.tempto.query.QueryResult;
import com.teradata.tempto.query.QueryType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class SimpleJdbcQueryExecutor
        implements QueryExecutor
{
    private final Connection connection;

    public SimpleJdbcQueryExecutor(Connection connection)
    {
        this.connection = requireNonNull(connection, "connection is null");
    }

    public QueryResult executeQuery(String sql, QueryType ignored, QueryParam... params)
            throws QueryExecutionException
    {
        return executeQuery(sql, params);
    }

    public QueryResult executeQuery(String sql, QueryParam... params)
            throws QueryExecutionException
    {
        checkArgument(params.length == 0, "Query parameters are not supported.");
        try (Statement statement = connection.createStatement()) {
            return QueryResult.forResultSet(statement.executeQuery(sql));
        }
        catch (SQLException e) {
            throw new QueryExecutionException(e);
        }
    }

    public Connection getConnection()
    {
        return connection;
    }

    public void close()
    {
        try {
            connection.close();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
